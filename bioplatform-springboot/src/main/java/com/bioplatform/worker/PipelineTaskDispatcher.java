package com.bioplatform.worker;

import com.bioplatform.engine.CommandRenderer;
import com.bioplatform.engine.ExecutionEngine;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.WorkflowTemplate;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.WorkflowTemplateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务调度器(2026-10-30 重构版,方案 B)
 *
 * 与旧版的本质区别:
 *  1. 命令来自模板渲染(CommandRenderer),不再把 dockerImage 当命令执行;
 *  2. taskId 落库,状态跟踪靠"调度线程扫 DB"(替代 @Async 睡眠轮询),
 *     backend 重启后自动接管,不会产生永久孤儿记录;
 *  3. 取消/超时真杀执行端进程(ExecutionEngine.cancel);
 *  4. 无可用 Worker / 渲染失败等一律落 FAILED 并带原因,不再静默 PENDING。
 *
 * @author luosg
 */
@Component
public class PipelineTaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskDispatcher.class);

    private final WorkerRegistry workerRegistry;
    private final ExecutionEngine executionEngine;
    private final PipelineExecutionMapper executionMapper;
    private final PipelineMapper pipelineMapper;
    private final WorkflowTemplateMapper workflowTemplateMapper;
    private final CommandRenderer commandRenderer;

    /** 上传卷在 backend 容器内的挂载根(输出目录在提交前由此创建) */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    public PipelineTaskDispatcher(WorkerRegistry workerRegistry,
                                  ExecutionEngine executionEngine,
                                  PipelineExecutionMapper executionMapper,
                                  PipelineMapper pipelineMapper,
                                  WorkflowTemplateMapper workflowTemplateMapper,
                                  CommandRenderer commandRenderer) {
        this.workerRegistry = workerRegistry;
        this.executionEngine = executionEngine;
        this.executionMapper = executionMapper;
        this.pipelineMapper = pipelineMapper;
        this.workflowTemplateMapper = workflowTemplateMapper;
        this.commandRenderer = commandRenderer;
    }

    /**
     * 调度执行任务到 Worker
     *
     * @param execution 已入库的 PENDING 执行记录
     * @return 是否成功提交(失败时记录已被标 FAILED 并写入原因)
     */
    public boolean dispatch(PipelineExecution execution) {
        WorkerRegistry.WorkerInfo worker = selectWorker();
        if (worker == null) {
            fail(execution, "当前无可用计算节点,任务未提交");
            return false;
        }

        Pipeline pipeline = pipelineMapper.selectById(execution.getPipelineId());
        if (pipeline == null) {
            fail(execution, "流水线不存在(pipelineId=" + execution.getPipelineId() + ")");
            return false;
        }
        WorkflowTemplate template = pipeline.getTemplateId() != null
                ? workflowTemplateMapper.selectById(pipeline.getTemplateId()) : null;
        if (template == null) {
            fail(execution, "流水线未关联工作流模板,无法生成命令");
            return false;
        }

        // Omics 模板(command_template 为空但有 snakemake_path)按仓库约定生成执行命令:
        //   pipeline → run.py -w <name> -m <meta> -o <out> --sdm apptainer <点号参数>
        //   task     → run_module.py --snakefile <smk> --config <merged.json> -o <out> --sdm apptainer [--samples ...]
        // 生成的命令仅内存生效;meta/合并配置由 backend 落到上传卷暂存目录。
        boolean omicsBridge = false;
        if (template.getCommandTemplate() == null || template.getCommandTemplate().isBlank()) {
            if (template.getSnakemakePath() == null || template.getSnakemakePath().isBlank()) {
                fail(execution, "模板未配置 command_template 且无 snakemake_path,无法执行");
                return false;
            }
            try {
                template.setCommandTemplate(buildOmicsCommand(template, pipeline, execution));
                omicsBridge = true;
            } catch (Exception e) {
                fail(execution, "生成 Omics 执行命令失败: " + e.getMessage());
                return false;
            }
        }

        // 流程版本(2026-11-26 方案一):Omics 桥接执行派发时采集仓库 HEAD commit,
        // 执行级事实一执行一版本;benchmark/校准全链按此版本分层。失败记 unknown。
        if (omicsBridge) {
            try {
                String ver = com.bioplatform.common.util.OmicsVersionUtil.resolveHead(omicsRoot);
                execution.setWorkflowVersion(ver);
                execution.setVersionSource(ver != null ? "git" : "unknown");
            } catch (Exception e) {
                execution.setVersionSource("unknown");
                log.warn("流程版本采集失败(执行继续): executionId={}, error={}", execution.getId(), e.getMessage());
            }
        }

        CommandRenderer.RenderedCommand rendered;
        try {
            rendered = commandRenderer.render(template, pipeline, execution);
        } catch (Exception e) {
            fail(execution, "命令渲染失败: " + e.getMessage());
            return false;
        }

        int timeoutSeconds = resolveTimeoutSeconds(pipeline);
        try {
            // 手写 command_template 的模板(如 docker run -v {{data_root}})依赖 backend 在共享卷
            // 预先创建输出目录:不能让模板自己 mkdir,worker 容器内 mkdir 落在容器文件系统,DooD 场景
            // docker run -v 绑的是宿主机路径,目录根本不会出现在宿主机卷上(2026-10-31 实测)。
            // Omics 桥接命令经 nsenter 在宿主机执行且自带 mkdir,输出根目录还可能在卷外,跳过预建。
            if (!omicsBridge) {
                java.nio.file.Files.createDirectories(
                        java.nio.file.Paths.get(sharedPath, rendered.outputDir()));
            }

            String taskId = executionEngine.submit(worker.getUrl(), execution,
                    rendered.command(), timeoutSeconds);

            execution.setWorkerId(worker.getId());
            execution.setWorkerUrl(worker.getUrl());
            execution.setTaskId(taskId);
            execution.setExecutor("local");
            execution.setStatus("RUNNING");
            // Omics 桥接的产物在 <输出根目录>/<pipelineId>(按分析复用目录,一层),
            // rendered.outputDir() 是旧 exec_<id> 布局,不能拿来当 output_path(进度/日志解析全靠它)
            execution.setOutputPath(omicsBridge
                    ? String.valueOf(execution.getPipelineId())
                    : rendered.outputDir());
            execution.setHeartbeatAt(LocalDateTime.now());
            // 执行输入快照(2026-11-26):meta/design/生效配置/渲染命令按派发时刻固化,
            // 执行详情可看全"这次执行实际用了什么"(流水线后续编辑不影响已执行记录的展示)
            try {
                com.fasterxml.jackson.databind.node.ObjectNode snap = MAPPER.createObjectNode();
                snap.put("meta", pipeline.getMetaContent());
                snap.put("design", pipeline.getDesignJson());
                Object cfg = mergedConfig(template, flattenedOverrides(pipeline));
                snap.set("config", MAPPER.valueToTree(cfg));
                snap.put("command", rendered.command());
                execution.setInputSnapshot(MAPPER.writeValueAsString(snap));
            } catch (Exception e) {
                log.warn("输入快照采集失败(执行继续): executionId={}, error={}", execution.getId(), e.getMessage());
            }
            executionMapper.updateById(execution);

            log.info("任务已调度: executionId={}, worker={}, taskId={}",
                    execution.getId(), worker.getId(), taskId);
            return true;
        } catch (Exception e) {
            fail(execution, "任务调度失败: " + e.getMessage());
            return false;
        }
    }

    // ==================== Omics 模板默认执行命令生成 ====================

    /** snakemake 运行环境(conda env smk;--sdm apptainer 容器模式不需要 conda 本体) */
    private static final String OMICS_ENV_PATH = "/home/luosg/miniforge3/envs/smk/bin";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Omics 仓库根(宿主机路径,经 worker 的 chroot /host 访问) */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.omics.root:/data/16T/luosg/workflow/Omics}")
    private String omicsRoot;

    /** 上传卷宿主机路径({{data_root}} 同源);暂存文件经卷写入,命令侧再拷进输出目录 */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.dataRoot:}")
    private String dataRoot;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bioplatform.service.SystemService systemService;

    @org.springframework.beans.factory.annotation.Autowired
    private WorkerClient workerClient;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bioplatform.common.util.ExecCallbackTokenUtil callbackTokenUtil;

    @org.springframework.beans.factory.annotation.Autowired
    private BenchmarkHarvester benchmarkHarvester;

    /** 执行回调地址(流程框架回报终态用):系统配置 execution.callbackUrl,宿主机侧默认本机后端 */
    private String resolveCallbackUrl() {
        String v = systemService.getConfigValue("execution.callbackUrl");
        return (v == null || v.isBlank())
                ? "http://127.0.0.1:8083/api/public/executions/callback" : v.trim();
    }

    /** 执行输出根目录:system_configs 的 execution.outputRoot(仅超管可改),空 = 默认上传卷 */
    private String resolveOutputRoot() {
        String v = systemService.getConfigValue("execution.outputRoot");
        return (v == null || v.isBlank()) ? dataRoot : v.trim();
    }

    /**
     * 为 Omics 模板生成执行命令(worker 经 nsenter 在宿主机命名空间执行)。
     * 产物落 <输出根目录>/<pipelineId>/ —— 按分析复用目录(一层,id 全局唯一):
     * 重跑时 snakemake 增量续跑(已完成步骤自动跳过),不支持同分析并发执行。
     * meta/合并配置由 backend 先写入上传卷暂存目录(<pid>/stage_exec_<id>),命令侧 mkdir+cp
     * 进输出目录——输出根目录可能在 backend 的 /host:ro 挂载之外,backend 直接写不进去。
     * 降权执行(2026-11-24 方案A):流程本体以输出根目录属主身份跑(setpriv),
     * 产物天然属主正确;尾句 chown --reference 保留作兜底(进程被强杀时另有
     * backend 终态补发 repairOmicsOwnership)。
     */
    private String buildOmicsCommand(WorkflowTemplate template, Pipeline pipeline, PipelineExecution execution) throws Exception {
        // 方案一(2026-11-24):--snakemake-args --rerun-incomplete——半成品输出自动重跑,
        // 根除 IncompleteFilesException 秒败类问题
        // 运行参数(2026-11-25):--forcerun 为 run.py 原生固定参数(强制重跑指定规则/文件
        // 并停在该处),从执行参数保留键 _forcerun 提取、逐 token 白名单校验;
        // pipeline 型走原生参数位,task 型(run_module 无原生 --forcerun)经
        // --snakemake-args 透传给 snakemake
        String forcerun = extractForcerun(execution);
        boolean pipelineType = "pipeline".equals(template.getType());
        String smkTail = (pipelineType || forcerun == null)
                ? "--rerun-incomplete"
                : forcerun + " --rerun-incomplete";
        return nsenterWrap(assembleOmicsRun(template, pipeline, String.valueOf(execution.getPipelineId()),
                execution.getProjectId() + "/stage_exec_" + execution.getId(),
                execution.getId(), execution.getId(),
                pipelineType ? forcerun : null, smkTail, true));
    }

    /** 执行参数保留键 _forcerun(2026-11-25):run.py 原生 --forcerun 的规则名/文件路径,
     *  空格分隔,逐 token 白名单(与 SAFE_META_FILE 同字符集,规则名与路径皆可);非法即拒 */
    private String extractForcerun(PipelineExecution execution) {
        if (execution.getInputParams() == null || execution.getInputParams().isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(execution.getInputParams());
            com.fasterxml.jackson.databind.JsonNode f = node.get("_forcerun");
            if (f == null || f.isNull() || f.asText().isBlank()) {
                return null;
            }
            StringBuilder sb = new StringBuilder("--forcerun");
            for (String t : f.asText().trim().split("\\s+")) {
                if (!SAFE_META_FILE.matcher(t).matches()) {
                    throw new IllegalArgumentException("强制重跑参数不安全(仅允许字母/数字/._-/): " + t);
                }
                sb.append(' ').append(t);
            }
            return sb.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null; // 非 JSON 的 inputParams(手写模板纯文本参数)不适用,跳过
        }
    }

    /**
     * 维护操作(2026-11-24 方案二):预检 dry-run / 解锁 unlock / 清理临时文件
     * delete-temp-output / 触碰时间戳 touch / 清理元数据 cleanup-metadata(需 extra=文件路径)。
     * 只收"终态型"操作(snakemake 执行即退出);--forcerun 会真跑工作流、属执行语义,
     * 故意不进维护通道(2026-11-25 安全复核撤下)。即时同步执行(≤5 分钟),
     * 输出经 <输出目录>/maintain.log 直读返回,不建执行记录(审计走 Controller @OperLog)。
     * 开关按模板型别映射:run.py 原生 --dry-run/--unlock/--touch;
     * run_module.py 仅原生 --dry-run/--list-rules,其余经 --snakemake-args 透传
     * (两脚本均有 REMAINDER 透传,2026-11-25 复核)。
     */
    public String runMaintenance(Pipeline pipeline, WorkflowTemplate template, String mode, String extra) throws Exception {
        boolean pipelineType = "pipeline".equals(template.getType());
        String nativeFlags;
        StringBuilder smk = new StringBuilder();
        switch (mode) {
            case "dry-run" -> {
                nativeFlags = "--dry-run";
                smk.append("--rerun-incomplete");
            }
            case "unlock" -> {
                nativeFlags = pipelineType ? "--unlock" : null;
                if (!pipelineType) smk.append("--unlock");
            }
            case "delete-temp-output" -> {
                nativeFlags = null;
                smk.append("--delete-temp-output");
            }
            case "touch" -> {
                nativeFlags = pipelineType ? "--touch" : null;
                if (!pipelineType) smk.append("--touch");
            }
            case "cleanup-metadata" -> {
                String[] files = extra == null || extra.isBlank() ? new String[0] : extra.trim().split("\\s+");
                if (files.length == 0) {
                    throw new IllegalArgumentException("cleanup-metadata 需要文件路径(extra)");
                }
                StringBuilder fs = new StringBuilder();
                for (String f : files) {
                    if (!SAFE_META_FILE.matcher(f).matches()) {
                        throw new IllegalArgumentException("文件路径不安全: " + f);
                    }
                    fs.append(' ').append(f);
                }
                nativeFlags = null;
                smk.append("--cleanup-metadata").append(fs);
            }
            default -> throw new IllegalArgumentException("未知维护操作: " + mode);
        }
        String modeFlags = nativeFlags;
        String smkTail = smk.length() == 0 ? null : smk.toString();
        String body = assembleOmicsRun(template, pipeline, String.valueOf(pipeline.getId()),
                pipeline.getProjectId() + "/stage_maintain_" + pipeline.getId(),
                null, null, modeFlags, smkTail, false);
        // 输出落 <输出目录>/maintain.log 由 backend 直读:worker shell 通道有 10KB 输出帽
        // (executeShellSync),预检报告会被截断(2026-11-24 e2e 实踩)。命令以 root 外壳
        // 重定向开文件,尾句 chown --reference 兜底属主。
        String absOut = resolveOutputRoot() + "/" + pipeline.getId();
        String cmd = body + " > \"" + absOut + "/maintain.log\" 2>&1";
        // executeShellSync 自带 nsenter 外壳——只传裸 body;
        WorkerRegistry.WorkerInfo worker = selectWorker();
        if (worker == null) {
            throw new IllegalStateException("当前无可用计算节点");
        }
        Map<String, Object> r = workerClient.executeShell(worker.getUrl(), cmd, 300, null);
        String out = readMaintainLog(absOut);
        if (out == null || out.isBlank()) {
            out = r.get("output") == null ? "" : String.valueOf(r.get("output"));
        }
        // 非零退出不当失败返回:预检/清理类操作的非零可能是"有工作要做"等语义,
        // 报告本身才是交付物;退出码附在尾行
        if (!Boolean.TRUE.equals(r.get("success"))) {
            out = out + "\n[命令退出码 " + r.get("exit_code") + "]";
        }
        return out;
    }

    /** 维护输出日志直读(backend 经 /host RO 挂载;绕开 worker 通道 10KB 输出帽) */
    private String readMaintainLog(String absOut) {
        try {
            java.nio.file.Path f = com.bioplatform.common.util.HostPathUtil
                    .resolveExisting(absOut + "/maintain.log");
            if (f == null) {
                return null;
            }
            String text = java.nio.file.Files.readString(f);
            return text.length() > 200_000 ? "…(输出过长已截断)\n" + text.substring(text.length() - 200_000) : text;
        } catch (Exception e) {
            return null;
        }
    }

    private String assembleOmicsRun(WorkflowTemplate template, Pipeline pipeline, String outputDirRel,
            String stageRel, Long logExecId, Long callbackExecId, String modeFlags, String smkTail,
            boolean redirectLog) throws Exception {
        String outRoot = resolveOutputRoot();
        String absOutStr = outRoot + "/" + outputDirRel;
        Files.createDirectories(Paths.get(sharedPath, stageRel));
        String stageHost = dataRoot + "/" + stageRel;

        Map<String, Object> overrides = flattenedOverrides(pipeline);
        List<String> copyFiles = new ArrayList<>();
        StringBuilder run = new StringBuilder();

        if ("pipeline".equals(template.getType())) {
            String metaArg;
            if ("path".equals(pipeline.getMetaType())
                    && pipeline.getMetaContent() != null && !pipeline.getMetaContent().isBlank()) {
                metaArg = "\"" + escDq(pipeline.getMetaContent().trim()) + "\"";
            } else {
                Files.writeString(Paths.get(sharedPath, stageRel, "meta.tsv"),
                        pipeline.getMetaContent() != null ? pipeline.getMetaContent() : "");
                copyFiles.add("meta.tsv");
                metaArg = "\"" + escDq(absOutStr + "/meta.tsv") + "\"";
            }
            // 实验设计关系(2026-11-25):样本信息"填入"带入的 design_json 落
            // design.json,经 run.py --design 消费(跳过 meta design 列,
            // group_pairs 由该文件生成);task 型(run_module)无此参数,不拼
            String designJson = pipeline.getDesignJson();
            if (designJson != null && !designJson.isBlank()) {
                Files.writeString(Paths.get(sharedPath, stageRel, "design.json"), designJson);
                copyFiles.add("design.json");
            }
            run.append("python3 run.py -w \"").append(escDq(template.getName()))
                    .append("\" -m ").append(metaArg)
                    .append(" -o \"").append(escDq(absOutStr)).append("\" --sdm apptainer")
                    // 降权后日志不能落仓库根(默认 workflow.log 在 cwd=Omics 根,root 遗留文件会 EACCES);
                    // 定向到输出目录,与 task_output 日志同处(2026-11-24 e2e 实踩)
                    .append(" --log \"").append(escDq(absOutStr + "/workflow.log")).append('"');
            if (designJson != null && !designJson.isBlank()) {
                run.append(" --design \"").append(escDq(absOutStr + "/design.json")).append('"');
            }
            // 参数覆盖(dot-notation)以 run.py 的点号 CLI 参数合并进模板配置
            for (Map.Entry<String, Object> e : overrides.entrySet()) {
                // 参数名注入防护(2026-11-24):key 直接拼在命令行上,白名单外拒绝
                if (!SAFE_PARAM_KEY.matcher(e.getKey()).matches()) {
                    throw new IllegalArgumentException("参数名不安全(仅允许字母/数字/._-): " + e.getKey());
                }
                run.append(' ').append(e.getKey())
                        .append("=\"").append(escDq(String.valueOf(e.getValue()))).append('"');
            }
            if (modeFlags != null) {
                run.append(' ').append(modeFlags);
            }
            if (smkTail != null) {
                // REMAINDER 语义:--snakemake-args 必须在所有 run.py 参数之后
                run.append(" --snakemake-args ").append(smkTail);
            }
        } else {
            // 模块:模板默认配置 + 参数覆盖 + meta 样本分组 合并落盘,run_module.py --config 引用
            ObjectNode cfg = (ObjectNode) mergedConfig(template, overrides);
            SampleGroups groups = sampleGroupsFromMeta(pipeline.getMetaContent());
            if (!groups.paired.isEmpty()) cfg.set("paired_samples", MAPPER.valueToTree(groups.paired));
            if (!groups.single.isEmpty()) cfg.set("single_samples", MAPPER.valueToTree(groups.single));
            Files.writeString(Paths.get(sharedPath, stageRel, "config.json"), MAPPER.writeValueAsString(cfg));
            copyFiles.add("config.json");
            run.append("python3 run_module.py --snakefile \"").append(escDq(template.getSnakemakePath()))
                    .append("\" --config \"").append(escDq(absOutStr + "/config.json"))
                    .append("\" -o \"").append(escDq(absOutStr)).append("\" --sdm apptainer");
            List<String> samples = groups.all();
            if (!samples.isEmpty()) {
                run.append(" --samples");
                for (String s : samples) {
                    run.append(" \"").append(escDq(s)).append('"');
                }
            }
            // run_module.py 无原生维护开关,但 --snakemake-args REMAINDER 透传齐备
            // (2026-11-25 复核:cmd += args.snakemake_args)——modeFlags 仅原生开关,
            // smkTail 两型一致透传
            if (modeFlags != null) {
                run.append(' ').append(modeFlags);
            }
            if (smkTail != null) {
                run.append(" --snakemake-args ").append(smkTail);
            }
        }

        // ===== 命令组装:root 段(属主准备/收尾) + 降权段(流程本体) =====
        // 降权执行(2026-11-24 方案A):流程以输出根目录属主身份跑
        // (setpriv --clear-groups 最小权限;实测 apptainer/sif、数据读、smk env 均可用,
        // 且降权段不碰上传卷——暂存拷贝在 root 段做)。stat 不可得/属主为 root
        // → 退化为 root 执行(现状行为)并打 WARN。
        String owner = resolveOutputOwner(outRoot); // "uid:gid" 或 null
        boolean dropPrivs = owner != null;

        // root 段 1:暂存拷贝(卷区 root 属主) + 输出目录/日志属主移交
        StringBuilder prep = new StringBuilder();
        if (!copyFiles.isEmpty()) {
            prep.append("cp");
            for (String f : copyFiles) {
                prep.append(" \"").append(escDq(stageHost + "/" + f)).append('"');
            }
            prep.append(" \"").append(escDq(absOutStr)).append("/\"; ");
        }
        if (dropPrivs) {
            prep.append("chown ").append(owner).append(" \"").append(escDq(absOutStr)).append("\"");
            for (String f : copyFiles) {
                prep.append(" \"").append(escDq(absOutStr + "/" + f)).append('"');
            }
            // 日志文件由外层重定向以 root 打开,一并移交属主(被杀场景不留 root 残留)
            if (logExecId != null) {
                prep.append(" \"").append(escDq(logPath(absOutStr, logExecId))).append("\"; ");
            } else {
                prep.append("; ");
            }
        }

        // 降权段:流程本体(环境变量/回调/run.py|run_module.py)
        StringBuilder flow = new StringBuilder();
        flow.append("export PATH=").append(OMICS_ENV_PATH).append(":$PATH; ");
        // 回调接口环境变量:流程框架终止/完成时回报终态(/api/public/executions/callback)
        if (callbackExecId != null) {
            flow.append("export BIPLATFORM_EXEC_ID=").append(callbackExecId).append("; ");
            flow.append("export BIPLATFORM_CALLBACK_TOKEN=\"")
                    .append(escDq(callbackTokenUtil.token(callbackExecId))).append("\"; ");
            flow.append("export BIPLATFORM_CALLBACK_URL=\"")
                    .append(escDq(resolveCallbackUrl())).append("\"; ");
        }
        flow.append("cd \"").append(escDq(omicsRoot)).append("\" && ").append(run);

        StringBuilder inner = new StringBuilder();
        inner.append(prep);
        if (dropPrivs) {
            String uid = owner.split(":")[0];
            String gid = owner.split(":")[1];
            // 降权段必须把 HOME/USER/LOGNAME 切到目标用户:root 的 HOME=/root 会让
            // snakemake/python 去写 /root/.cache 直接 EACCES(2026-11-24 e2e 实踩)
            inner.append("setpriv --reuid=").append(uid)
                    .append(" --regid=").append(gid)
                    .append(" --clear-groups /bin/bash -c '")
                    .append("export USER=$(getent passwd ").append(uid)
                    .append(" | cut -d: -f1) HOME=$(getent passwd ").append(uid)
                    .append(" | cut -d: -f6) LOGNAME=$USER; ")
                    .append(flow).append("'");
        } else {
            inner.append(flow);
        }
        // 产物属主兜底 chown + 暂存目录清理;保留运行退出码。
        // chown 报错进 task log(不再 >/dev/null 吞掉);进程被强杀跑不到本句时,
        // 由 backend 在终态后经 worker shell 补发 repairOmicsOwnership(幂等)
        inner.append("; rc=$?; chown -R --reference=\"").append(escDq(outRoot)).append("\" \"")
                .append(escDq(absOutStr)).append("\"; rm -rf \"")
                .append(escDq(stageHost)).append("\"; exit $rc");

        // 外层经 nsenter 进宿主机命名空间运行(用户态 apptainer 的 newuidmap 流程只在宿主机命名空间可用;
        // worker 容器 privileged + pid:host 才能 nsenter -t 1)。同 ShellExecuteTool 的宿主机语义。
        // -n(网络空间)必须带上:回调打 127.0.0.1:8083 要宿主机网络;任务内联网(如 KEGG REST)
        // 依赖宿主机 DNS——不进 -n 时挂载空间是宿主机的 resolv.conf(127.0.0.53)而网络空间是容器的,
        // DNS 解析必挂("Could not resolve hostname")。
        // 全程输出实时 tee 到 <输出目录>/task_output.<执行id>.log(执行监控的日志/进度来源;
        // 目录按分析复用,日志按执行独立,互不覆盖)
        String body = "mkdir -p \"" + absOutStr.replace("\"", "\\\"") + "\" && { "
                + inner + " ; }"
                + (redirectLog ? " >> \"" + logPath(absOutStr, logExecId) + "\" 2>&1" : "");
        return body;
    }

    /** nsenter 外壳(任务提交路径用)。注意 worker 的 executeShellSync 自带 nsenter,
     *  经 shell 通道执行的命令只能传裸 body,不能重复包裹 */
    private String nsenterWrap(String body) {
        return "nsenter -t 1 -m -u -i -p -n --preserve-credentials /bin/bash -c '"
                + body.replace("'", "'\"'\"'") + "'";
    }

    private static String logPath(String absOutStr, Long executionId) {
        return absOutStr + "/task_output." + executionId + ".log";
    }

    /**
     * 输出根目录属主 uid:gid(降权执行目标)。
     * 不可得/属主为 root 返回 null → 退化为 root 执行(现状行为,打 WARN)。
     * backend 经 /host/data/16T 只读挂载读 unix:uid/gid 属性,HostPathUtil 处理前缀映射。
     */
    private String resolveOutputOwner(String outRoot) {
        try {
            java.nio.file.Path p = com.bioplatform.common.util.HostPathUtil.resolveExisting(outRoot);
            int uid = (Integer) java.nio.file.Files.getAttribute(p, "unix:uid");
            int gid = (Integer) java.nio.file.Files.getAttribute(p, "unix:gid");
            if (uid == 0) {
                log.warn("输出根目录属主为 root,流程将以 root 执行(建议输出根指向用户目录): {}", outRoot);
                return null;
            }
            return uid + ":" + gid;
        } catch (Exception e) {
            log.warn("输出根目录属主不可得,流程退化为 root 执行: outRoot={}, error={}", outRoot, e.getMessage());
            return null;
        }
    }

    /**
     * 任务超时(秒):pipeline.timeout 显式设置优先,其次系统配置 execution.timeoutMinutes
     * (执行配置,仅超管可改),默认 24h——RNAseq 等大流程按旧缺省 1h 会被中途强杀。
     */
    private int resolveTimeoutSeconds(Pipeline pipeline) {
        if (pipeline != null && pipeline.getTimeout() != null) {
            return pipeline.getTimeout();
        }
        try {
            String v = systemService.getConfigValue("execution.timeoutMinutes");
            if (v != null && !v.isBlank()) {
                return Math.max(1, Integer.parseInt(v.trim())) * 60;
            }
        } catch (Exception ignored) {
            // 配置不可用时走缺省
        }
        return 86400;
    }

    /** extraParams(dot-notation 或嵌套 JSON)拍平成 dot 键值 */
    private Map<String, Object> flattenedOverrides(Pipeline pipeline) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        if (pipeline.getExtraParams() == null || pipeline.getExtraParams().isBlank()) {
            return out;
        }
        flattenNode("", MAPPER.readTree(pipeline.getExtraParams()), out);
        return out;
    }

    private void flattenNode(String path, JsonNode node, Map<String, Object> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e ->
                    flattenNode(path.isEmpty() ? e.getKey() : path + "." + e.getKey(), e.getValue(), out));
        } else if (!path.isEmpty()) {
            out.put(path, node.isValueNode() ? node.asText() : node.toString());
        }
    }

    /** 模板默认配置 + 点号覆盖 → 嵌套 config JSON(dot 键自动展开,覆盖优先) */
    private JsonNode mergedConfig(WorkflowTemplate template, Map<String, Object> overrides) throws Exception {
        JsonNode base = MAPPER.readTree(template.getConfigTemplate() == null || template.getConfigTemplate().isBlank()
                ? "{}" : template.getConfigTemplate());
        ObjectNode root = base.isObject() ? (ObjectNode) base : MAPPER.createObjectNode();
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            ObjectNode node = root;
            String[] segs = e.getKey().split("\\.");
            for (int i = 0; i < segs.length - 1; i++) {
                JsonNode child = node.get(segs[i]);
                if (!(child instanceof ObjectNode)) {
                    child = node.putObject(segs[i]);
                }
                node = (ObjectNode) child;
            }
            node.set(segs[segs.length - 1], MAPPER.valueToTree(String.valueOf(e.getValue())));
        }
        return root;
    }

    /** meta TSV 样本分组:第一列 sample_id,含 fastq_2 列非空的样本为双端,否则单端 */
    private static final class SampleGroups {
        final List<String> paired = new ArrayList<>();
        final List<String> single = new ArrayList<>();

        List<String> all() {
            List<String> a = new ArrayList<>(paired);
            a.addAll(single);
            return a;
        }
    }

    private SampleGroups sampleGroupsFromMeta(String metaContent) {
        SampleGroups g = new SampleGroups();
        if (metaContent == null || metaContent.isBlank()) {
            return g;
        }
        String[] lines = metaContent.split("\\R");
        int idxSample = 0, idxF2 = -1;
        for (int i = 0; i < lines.length; i++) {
            String[] cells = lines[i].split("\t", -1);
            if (i == 0 && lines[0].toLowerCase().contains("sample")) {
                for (int c = 0; c < cells.length; c++) {
                    String h = cells[c].trim().toLowerCase();
                    if (h.contains("sample")) idxSample = c;
                    if (h.contains("fastq_2") || h.contains("fq_2") || h.equals("r2") || h.endsWith("_r2")) idxF2 = c;
                }
                continue;
            }
            String sample = cells.length > idxSample ? cells[idxSample].trim() : "";
            if (sample.isEmpty()) continue;
            String f2 = idxF2 >= 0 && cells.length > idxF2 ? cells[idxF2].trim() : "";
            if (idxF2 >= 0 && !f2.isEmpty()) {
                g.paired.add(sample);
            } else {
                g.single.add(sample);
            }
        }
        return g;
    }

    /** 参数 key 安全字符集(dot-notation CLI 参数名,防命令注入,2026-11-24) */
    private static final java.util.regex.Pattern SAFE_PARAM_KEY =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_][A-Za-z0-9_.\\-]*$");

    /** 维护操作文件路径字符集(cleanup-metadata 的 extra,防命令注入) */
    private static final java.util.regex.Pattern SAFE_META_FILE =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._/\\-]+$");

    /** 双引号内转义(外层整体再以单引号包裹给 bash -c) */
    private String escDq(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`");
    }

    /**
     * 调度线程:每 10 秒扫一遍 RUNNING 执行记录并向执行端对账。
     * backend 重启后该线程自动接管所有 RUNNING 记录 —— 这就是"不产生孤儿"的机制。
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    public void pollRunningExecutions() {
        PipelineExecution query = new PipelineExecution();
        query.setStatus("RUNNING");
        List<PipelineExecution> running;
        try {
            running = executionMapper.selectAll(query);
        } catch (Exception e) {
            log.warn("扫描执行记录失败(下轮重试): {}", e.getMessage());
            return;
        }

        for (PipelineExecution exec : running) {
            if (exec.getTaskId() == null || exec.getWorkerUrl() == null) {
                fail(exec, "执行记录缺少 taskId/workerUrl(调度中断的旧数据),请重新执行");
                continue;
            }
            try {
                applyStatus(exec, executionEngine.status(exec.getWorkerUrl(), exec.getTaskId()));
            } catch (Exception e) {
                // 网络级瞬断:心跳超 5 分钟仍不可达才判死
                if (exec.getHeartbeatAt() != null
                        && exec.getHeartbeatAt().plusMinutes(5).isBefore(LocalDateTime.now())) {
                    fail(exec, "Worker 不可达超过 5 分钟且状态无法确认,标记失败");
                } else {
                    log.warn("轮询失败(下轮重试): executionId={}, error={}", exec.getId(), e.getMessage());
                }
            }
        }
    }

    /** 供 service 拉取 worker 端任务输出(执行监控"执行结果"的兜底来源) */
    public String fetchTaskOutput(PipelineExecution exec) {
        try {
            return executionEngine.output(exec.getWorkerUrl(), exec.getTaskId());
        } catch (Exception e) {
            return "";
        }
    }

    /** 终态时把 worker 抓取的执行输出落盘到输出目录 task_output.log(执行监控的日志来源) */
    private void persistTaskOutput(PipelineExecution exec) {
        writeTaskOutputFile(exec, executionEngine.output(exec.getWorkerUrl(), exec.getTaskId()));
    }

    private void writeTaskOutputFile(PipelineExecution exec, String output) {
        try {
            if (exec.getOutputPath() == null || exec.getOutputPath().isBlank()) {
                return;
            }
            java.nio.file.Path f = java.nio.file.Paths.get(sharedPath, exec.getOutputPath(), "task_output.log");
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.writeString(f, output == null ? "" : output);
        } catch (Exception e) {
            log.warn("执行输出落盘失败(不影响结果): executionId={}, error={}", exec.getId(), e.getMessage());
        }
    }

    /**
     * 产物活跃度探测:输出目录树 10 分钟内有写入 = 执行进程仍存在。
     * 递归查新(限深 4):顶层目录 mtime 只在增删直接子项时变化,
     * 长流程只写深层文件会误判静默、把仍在跑的执行提前定案。
     */
    private boolean isOutputActive(PipelineExecution exec) {
        if (exec.getOutputPath() == null || exec.getOutputPath().isBlank()) {
            return false;
        }
        java.time.Instant cutoff = java.time.LocalDateTime.now().minusMinutes(10)
                .atZone(java.time.ZoneId.systemDefault()).toInstant();
        for (String root : new String[]{resolveOutputRoot(), dataRoot, sharedPath}) {
            if (root == null || root.isBlank()) {
                continue;
            }
            try {
                java.nio.file.Path dir = com.bioplatform.common.util.HostPathUtil.resolveExisting(
                        root + "/" + exec.getOutputPath());
                if (!java.nio.file.Files.isDirectory(dir)) {
                    continue;
                }
                if (java.nio.file.Files.getLastModifiedTime(dir).toInstant().isAfter(cutoff)) {
                    return true;
                }
                try (var stream = java.nio.file.Files.walk(dir, 4)) {
                    for (java.nio.file.Path p : stream.filter(java.nio.file.Files::isRegularFile).toList()) {
                        if (java.nio.file.Files.getLastModifiedTime(p).toInstant().isAfter(cutoff)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
                // 该根下不可读,试下一个
            }
        }
        return false;
    }

    private void applyStatus(PipelineExecution exec, String status) {
        switch (status) {
            case "COMPLETED" -> {
                exec.setStatus("SUCCESS");
                persistTaskOutput(exec);
                touch(exec);
                log.info("任务完成: executionId={}, taskId={}", exec.getId(), exec.getTaskId());
                repairOmicsOwnership(exec);
            }
            case "FAILED", "CANCELLED" -> {
                // 执行进程还在写产物时,worker 的终态可能只是上一世代任务记录的陈旧结论
                // (旧超时强杀留下的孤儿仍在跑)。产物安静后才定案,期间保持 RUNNING。
                if (isOutputActive(exec)) {
                    exec.setHeartbeatAt(LocalDateTime.now());
                    executionMapper.updateById(exec);
                    log.info("Worker 报终态 {} 但产物仍在写入,保持运行待定案: executionId={}", status, exec.getId());
                } else if ("FAILED".equals(status)) {
                    String output = executionEngine.output(exec.getWorkerUrl(), exec.getTaskId());
                    exec.setStatus("FAILED");
                    exec.setErrorLog(truncate(output));
                    writeTaskOutputFile(exec, output);
                    touch(exec);
                    log.warn("任务失败: executionId={}, taskId={}", exec.getId(), exec.getTaskId());
                    repairOmicsOwnership(exec);
                } else {
                    exec.setStatus("CANCELLED");
                    persistTaskOutput(exec);
                    touch(exec);
                    log.info("任务已取消: executionId={}", exec.getId());
                    repairOmicsOwnership(exec);
                }
            }
            case "NOT_FOUND" -> {
                // 探测机制:worker 丢了任务记录 ≠ 执行进程死了(旧超时强杀曾留下仍在跑的孤儿)。
                // 产物目录仍有近期写入 = 进程还活着,保持 RUNNING;确认无活动才判失败。
                if (isOutputActive(exec)) {
                    exec.setHeartbeatAt(LocalDateTime.now());
                    executionMapper.updateById(exec);
                    log.info("Worker 端无任务记录但产物仍在写入,按探测机制保持运行: executionId={}", exec.getId());
                } else {
                    fail(exec, "执行进程不存在且产物无近期活动(worker 可能重启),请重新执行");
                }
            }
            default -> {
                // RUNNING/PENDING/UNKNOWN:仅刷新心跳
                exec.setHeartbeatAt(LocalDateTime.now());
                executionMapper.updateById(exec);
            }
        }
    }

    /**
     * 探测机制(2026-11-13 取代超时强杀):
     * 不再按墙钟超时杀任务——pollRunningExecutions 每 10 秒向执行端探测任务/进程是否存在,
     * 仅在确定性丢失(status=NOT_FOUND 或 worker 失联 5 分钟)时判失败;
     * 长流程跑多久都允许,流程框架可在终止/完成时经回调接口回报终态。
     */

    /**
     * 启动对账:backend 重启后立即把无主的 RUNNING 记录拉回对账流程
     * (第一条调度线程 tick 会真正处理;这里仅打印现状便于日志排查)
     */
    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        PipelineExecution query = new PipelineExecution();
        query.setStatus("RUNNING");
        try {
            List<PipelineExecution> running = executionMapper.selectAll(query);
            if (!running.isEmpty()) {
                log.info("启动对账: 发现 {} 条 RUNNING 执行记录,将由调度线程接管对账", running.size());
            }
        } catch (Exception e) {
            log.warn("启动对账查询失败: {}", e.getMessage());
        }
    }

    /**
     * 取消任务(真杀执行端进程)
     *
     * @return 是否执行了取消(状态不允许时 false)
     */
    public boolean cancelExecution(Long executionId) {
        PipelineExecution execution = executionMapper.selectById(executionId);
        if (execution == null) {
            return false;
        }
        String status = execution.getStatus();
        if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
            return false;
        }
        if ("RUNNING".equals(status) && execution.getTaskId() != null && execution.getWorkerUrl() != null) {
            try {
                executionEngine.cancel(execution.getWorkerUrl(), execution.getTaskId());
            } catch (Exception e) {
                log.warn("取消调用失败,仍将标记为已取消: executionId={}, error={}", executionId, e.getMessage());
            }
        }
        execution.setStatus("CANCELLED");
        execution.setFinishedAt(LocalDateTime.now());
        execution.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(execution);
        log.info("任务已取消: executionId={}", executionId);
        repairOmicsOwnership(execution);
        harvestBenchmarks(execution);
        return true;
    }

    /**
     * 选择负载最低的 Worker(空闲内存最多)
     */
    private WorkerRegistry.WorkerInfo selectWorker() {
        List<WorkerRegistry.WorkerInfo> healthyWorkers = workerRegistry.getHealthyWorkers();
        if (healthyWorkers.isEmpty()) {
            return null;
        }
        WorkerRegistry.WorkerInfo best = null;
        for (WorkerRegistry.WorkerInfo w : healthyWorkers) {
            if (best == null || w.getFreeMemoryGB() > best.getFreeMemoryGB()) {
                best = w;
            }
        }
        return best;
    }

    private void touch(PipelineExecution exec) {
        exec.setFinishedAt(LocalDateTime.now());
        exec.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(exec);
        harvestBenchmarks(exec);
    }

    private void fail(PipelineExecution exec, String reason) {
        exec.setStatus("FAILED");
        exec.setErrorLog(reason);
        exec.setFinishedAt(LocalDateTime.now());
        exec.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(exec);
        log.warn("执行失败: executionId={}, reason={}", exec.getId(), reason);
        repairOmicsOwnership(exec);
        harvestBenchmarks(exec);
    }

    /**
     * 终态 benchmark 收割(方案一,异步幂等):成功/失败/取消/判死全分支收口到
     * touch/fail/cancelExecution 三处,回调终态在 PublicExecutionCallbackController
     * 另行调用。失败跑也采(删失数据)。
     */
    public void harvestBenchmarks(PipelineExecution exec) {
        new Thread(() -> benchmarkHarvester.harvestWindow(exec), "bench-harvest-" + exec.getId()).start();
    }

    /**
     * Omics 桥接产物属主修复(幂等,终态后补发):
     * 命令尾句 chown 在进程被强杀(取消/对账判死/worker 重启)时跑不到 → 产物残留 root 属主
     * (2026-11-24 实例:执行35 FAILED 后 results/22/ 整树 root:root)。终态时 backend 经
     * worker shell 端点(nsenter 宿主机命名空间)补发 chown --reference,属主跟随输出根目录;
     * 正常退出时尾句已 chown,重复执行无副作用。延迟数秒等被杀进程的文件写入落定。
     */
    public void repairOmicsOwnership(PipelineExecution exec) {
        if (exec == null || exec.getTaskId() == null || exec.getWorkerUrl() == null) {
            return; // 从未提交到执行端,无产物
        }
        try {
            Pipeline pipeline = pipelineMapper.selectById(exec.getPipelineId());
            if (pipeline == null || pipeline.getTemplateId() == null) {
                return;
            }
            WorkflowTemplate template = workflowTemplateMapper.selectById(pipeline.getTemplateId());
            if (template == null) {
                return;
            }
            boolean omicsBridge = (template.getCommandTemplate() == null || template.getCommandTemplate().isBlank())
                    && template.getSnakemakePath() != null && !template.getSnakemakePath().isBlank();
            if (!omicsBridge) {
                return; // 手写模板产物在共享卷(root 属主常态),不属本机制
            }
            String outRoot = resolveOutputRoot();
            String absOut = outRoot + "/" + exec.getPipelineId();
            String cmd = "chown -R --reference=\"" + escDq(outRoot) + "\" \"" + escDq(absOut) + "\"";
            String workerUrl = exec.getWorkerUrl();
            Long executionId = exec.getId();
            new Thread(() -> {
                try {
                    Thread.sleep(5_000); // 等被杀进程的在途写入落定,避免 chown 后又冒出 root 文件
                    java.util.Map<String, Object> r = workerClient.executeShell(workerUrl, cmd, 600, null);
                    log.info("产物属主修复完成: executionId={}, exitCode={}", executionId, r.get("exit_code"));
                } catch (Exception e) {
                    log.warn("产物属主修复失败: executionId={}, error={}", executionId, e.getMessage());
                }
            }, "ownership-repair-" + exec.getId()).start();
        } catch (Exception e) {
            log.warn("产物属主修复调度失败: executionId={}, error={}", exec.getId(), e.getMessage());
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 8000 ? s.substring(0, 8000) + "\n...[输出截断]" : s;
    }
}
