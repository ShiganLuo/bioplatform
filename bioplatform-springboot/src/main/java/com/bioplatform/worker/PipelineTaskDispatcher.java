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

        CommandRenderer.RenderedCommand rendered;
        try {
            rendered = commandRenderer.render(template, pipeline, execution);
        } catch (Exception e) {
            fail(execution, "命令渲染失败: " + e.getMessage());
            return false;
        }

        int timeoutSeconds = pipeline.getTimeout() != null ? pipeline.getTimeout() : 3600;
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
            execution.setOutputPath(rendered.outputDir());
            execution.setHeartbeatAt(LocalDateTime.now());
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

    /** 执行输出根目录:system_configs 的 execution.outputRoot(仅超管可改),空 = 默认上传卷 */
    private String resolveOutputRoot() {
        String v = systemService.getConfigValue("execution.outputRoot");
        return (v == null || v.isBlank()) ? dataRoot : v.trim();
    }

    /**
     * 为 Omics 模板生成执行命令(worker 经 nsenter 在宿主机命名空间执行)。
     * 产物落 <输出根目录>/<projectId>/results/exec_<id>/;meta/合并配置由 backend 先写入
     * 上传卷暂存目录(<pid>/stage_exec_<id>),命令侧 mkdir+cp 进输出目录——输出根目录可能
     * 在 backend 的 /host:ro 挂载之外,backend 直接写不进去。
     * 结束后 chown --reference=输出根目录:产物属主跟随根目录属主,指向自己的数据目录即摆脱 root。
     */
    private String buildOmicsCommand(WorkflowTemplate template, Pipeline pipeline, PipelineExecution execution) throws Exception {
        String outputDirRel = execution.getProjectId() + "/results/exec_" + execution.getId();
        String outRoot = resolveOutputRoot();
        String absOutStr = outRoot + "/" + outputDirRel;
        String stageRel = execution.getProjectId() + "/stage_exec_" + execution.getId();
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
            run.append("python3 run.py -w \"").append(escDq(template.getName()))
                    .append("\" -m ").append(metaArg)
                    .append(" -o \"").append(escDq(absOutStr)).append("\" -t 16 --sdm apptainer");
            // 参数覆盖(dot-notation)以 run.py 的点号 CLI 参数合并进模板配置
            for (Map.Entry<String, Object> e : overrides.entrySet()) {
                run.append(' ').append(e.getKey())
                        .append("=\"").append(escDq(String.valueOf(e.getValue()))).append('"');
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
                    .append("\" -o \"").append(escDq(absOutStr)).append("\" -t 8 --sdm apptainer");
            List<String> samples = groups.all();
            if (!samples.isEmpty()) {
                run.append(" --samples");
                for (String s : samples) {
                    run.append(" \"").append(escDq(s)).append('"');
                }
            }
        }

        StringBuilder inner = new StringBuilder();
        inner.append("export PATH=").append(OMICS_ENV_PATH).append(":$PATH; ");
        inner.append("mkdir -p \"").append(escDq(absOutStr)).append("\" || exit 1; ");
        if (!copyFiles.isEmpty()) {
            inner.append("cp");
            for (String f : copyFiles) {
                inner.append(" \"").append(escDq(stageHost + "/" + f)).append('"');
            }
            inner.append(" \"").append(escDq(absOutStr)).append("/\"; ");
        }
        inner.append("cd \"").append(escDq(omicsRoot)).append("\" && ").append(run);
        // 产物属主跟随输出根目录属主(chown 到项目层,连 results/exec 包装目录一并摆脱 root);
        // 暂存目录用完即删;保留运行退出码
        inner.append("; rc=$?; chown -R --reference=\"").append(escDq(outRoot)).append("\" \"")
                .append(escDq(outRoot + "/" + execution.getProjectId())).append("\" >/dev/null 2>&1; rm -rf \"")
                .append(escDq(stageHost)).append("\"; exit $rc");

        // 外层经 nsenter 进宿主机命名空间运行(用户态 apptainer 的 newuidmap 流程只在宿主机命名空间可用;
        // worker 容器 privileged + pid:host 才能 nsenter -t 1)。同 ShellExecuteTool 的宿主机语义。
        return "nsenter -t 1 -m -u -i -p --preserve-credentials /bin/bash -c '"
                + inner.toString().replace("'", "'\"'\"'") + "'";
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

    private void applyStatus(PipelineExecution exec, String status) {
        switch (status) {
            case "COMPLETED" -> {
                exec.setStatus("SUCCESS");
                persistTaskOutput(exec);
                touch(exec);
                log.info("任务完成: executionId={}, taskId={}", exec.getId(), exec.getTaskId());
            }
            case "FAILED" -> {
                String output = executionEngine.output(exec.getWorkerUrl(), exec.getTaskId());
                exec.setStatus("FAILED");
                exec.setErrorLog(truncate(output));
                writeTaskOutputFile(exec, output);
                touch(exec);
                log.warn("任务失败: executionId={}, taskId={}", exec.getId(), exec.getTaskId());
            }
            case "CANCELLED" -> {
                exec.setStatus("CANCELLED");
                persistTaskOutput(exec);
                touch(exec);
                log.info("任务已取消: executionId={}", exec.getId());
            }
            case "NOT_FOUND" ->
                    fail(exec, "任务在 Worker 端丢失(worker 可能重启),请重新执行");
            default -> {
                // RUNNING/PENDING/UNKNOWN:仅刷新心跳
                exec.setHeartbeatAt(LocalDateTime.now());
                executionMapper.updateById(exec);
            }
        }
    }

    /**
     * 超时强查:超过 pipeline.timeout 的 RUNNING 任务,真杀执行端进程后标 FAILED。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 25_000)
    public void enforceTimeouts() {
        PipelineExecution query = new PipelineExecution();
        query.setStatus("RUNNING");
        List<PipelineExecution> running;
        try {
            running = executionMapper.selectAll(query);
        } catch (Exception e) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PipelineExecution exec : running) {
            if (exec.getStartedAt() == null) continue;
            Pipeline pipeline = pipelineMapper.selectById(exec.getPipelineId());
            int timeoutSeconds = pipeline != null && pipeline.getTimeout() != null
                    ? pipeline.getTimeout() : 3600;
            if (exec.getStartedAt().plusSeconds(timeoutSeconds).isBefore(now)) {
                if (exec.getTaskId() != null && exec.getWorkerUrl() != null) {
                    try {
                        executionEngine.cancel(exec.getWorkerUrl(), exec.getTaskId());
                    } catch (Exception e) {
                        log.warn("超时任务取消调用失败: executionId={}, error={}", exec.getId(), e.getMessage());
                    }
                }
                fail(exec, "任务执行超时(超过 " + timeoutSeconds + " 秒),已强制终止");
            }
        }
    }

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
    }

    private void fail(PipelineExecution exec, String reason) {
        exec.setStatus("FAILED");
        exec.setErrorLog(reason);
        exec.setFinishedAt(LocalDateTime.now());
        exec.setHeartbeatAt(LocalDateTime.now());
        executionMapper.updateById(exec);
        log.warn("执行失败: executionId={}, reason={}", exec.getId(), reason);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 8000 ? s.substring(0, 8000) + "\n...[输出截断]" : s;
    }
}
