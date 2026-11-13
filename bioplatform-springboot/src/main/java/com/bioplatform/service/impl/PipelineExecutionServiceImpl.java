package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.ExecutionVO;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.Project;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.service.PipelineExecutionService;
import com.bioplatform.worker.PipelineTaskDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流水线执行记录服务实现类
 *
 * @author luosg
 */
@Service
public class PipelineExecutionServiceImpl implements PipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionServiceImpl.class);

    private final PipelineExecutionMapper pipelineExecutionMapper;
    private final PipelineTaskDispatcher taskDispatcher;
    private final PipelineMapper pipelineMapper;
    private final ProjectMapper projectMapper;

    /** 上传卷挂载根(task_output.log 落盘位置的读取侧) */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    /** 上传卷宿主机路径(与 task_output.log 读取同源兜底) */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.storage.dataRoot:}")
    private String dataRoot;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bioplatform.service.SystemService systemService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PipelineExecutionServiceImpl(PipelineExecutionMapper pipelineExecutionMapper,
                                        PipelineTaskDispatcher taskDispatcher,
                                        PipelineMapper pipelineMapper,
                                        ProjectMapper projectMapper) {
        this.pipelineExecutionMapper = pipelineExecutionMapper;
        this.taskDispatcher = taskDispatcher;
        this.pipelineMapper = pipelineMapper;
        this.projectMapper = projectMapper;
    }

    @Override
    public PipelineExecution getExecutionById(Long id) {
        return pipelineExecutionMapper.selectById(id);
    }

    @Override
    public PageResult listByProjectId(Long projectId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByProjectId(projectId, null);
        PageInfo<PipelineExecution> pageInfo = new PageInfo<>(executions);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, toVOList(executions, false));
    }

    @Override
    public PageResult listByUserId(Long userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByUserId(userId, null);
        PageInfo<PipelineExecution> pageInfo = new PageInfo<>(executions);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, toVOList(executions, false));
    }

    @Override
    public ExecutionVO getExecutionVO(Long id) {
        PipelineExecution e = pipelineExecutionMapper.selectById(id);
        return e == null ? null : toVO(e, true);
    }

    // ==================== VO 组装(字段对齐前端) ====================

    private List<ExecutionVO> toVOList(List<PipelineExecution> list, boolean withResult) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        java.util.Map<Long, String> pipelineNames = new java.util.HashMap<>();
        java.util.Map<Long, String> projectNames = new java.util.HashMap<>();
        for (PipelineExecution e : list) {
            if (e.getPipelineId() != null && !pipelineNames.containsKey(e.getPipelineId())) {
                Pipeline p = pipelineMapper.selectById(e.getPipelineId());
                pipelineNames.put(e.getPipelineId(), p == null ? null : p.getName());
            }
            if (e.getProjectId() != null && !projectNames.containsKey(e.getProjectId())) {
                Project p = projectMapper.selectById(e.getProjectId());
                projectNames.put(e.getProjectId(), p == null ? null : p.getName());
            }
        }
        return list.stream()
                .map(e -> toVO(e, withResult, pipelineNames.get(e.getPipelineId()), projectNames.get(e.getProjectId())))
                .collect(Collectors.toList());
    }

    private ExecutionVO toVO(PipelineExecution e, boolean withResult) {
        return toVO(e, withResult, null, null);
    }

    private ExecutionVO toVO(PipelineExecution e, boolean withResult, String pipelineName, String projectName) {
        if (pipelineName == null && e.getPipelineId() != null) {
            Pipeline p = pipelineMapper.selectById(e.getPipelineId());
            pipelineName = p == null ? null : p.getName();
        }
        if (projectName == null && e.getProjectId() != null) {
            Project p = projectMapper.selectById(e.getProjectId());
            projectName = p == null ? null : p.getName();
        }
        String result = withResult ? resolveLogText(e) : null;
        // 进度:progress.jsonl(logger 插件)> snakemake 控制台 > task_output(见 progressInfo)
        ProgressInfo pi = progressInfo(e, result);
        return new ExecutionVO(
                e.getId(), e.getPipelineId(), pipelineName, e.getProjectId(), projectName, e.getUserId(),
                e.getStatus(),
                e.getInputParams(),
                result,
                e.getErrorLog(),
                pi.percent(),
                pi.text(),
                e.getExecutor(),
                e.getOutputPath(),
                e.getTaskId(),
                fmt(e.getStartedAt()),
                fmt(e.getFinishedAt()),
                fmt(e.getCreatedAt()));
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }

    private record ProgressInfo(Integer percent, String text) {
    }

    /**
     * 进度解析(B 层优先,逐级兜底):
     * 1) progress.jsonl —— snakemake logger 插件事件(框架/插件写入,结构化);
     * 2) 工作目录 .snakemake/log/*.log —— snakemake 控制台(流水线运行的真实进度源);
     * 3) task_output.<执行id>.log —— 模块运行的控制台。
     * text 形如 "24/105 · RNAseq_quantify",未产生进度事件时为"准备中(DAG 构建)"。
     */
    private ProgressInfo progressInfo(PipelineExecution e, String resultText) {
        if ("SUCCESS".equals(e.getStatus())) {
            return new ProgressInfo(100, "完成");
        }
        ProgressInfo fromJsonl = parseProgressJsonl(e);
        if (fromJsonl != null) {
            return fromJsonl;
        }
        String text = tailOf(newestSnakemakeLog(e), 16_384);
        if (text == null || text.isBlank()) {
            text = tailOf(resolveTaskLog(e), 16_384);
        }
        if (text == null || text.isBlank()) {
            text = resultText;
        }
        return parseSnakemakeText(text, e.getStatus());
    }

    /** B 层:progress.jsonl 最后一条 progress 事件 + 最新 job 事件的规则名 */
    private ProgressInfo parseProgressJsonl(PipelineExecution e) {
        java.nio.file.Path f = resolveUnderOutput(e, "progress.jsonl");
        if (f == null) {
            return null;
        }
        try {
            List<String> lines = java.nio.file.Files.readAllLines(f);
            int from = Math.max(0, lines.size() - 200);
            java.util.regex.Pattern pj = java.util.regex.Pattern.compile("\"completed_jobs\"\\s*:\\s*(\\d+)");
            java.util.regex.Pattern pt = java.util.regex.Pattern.compile("\"total_jobs\"\\s*:\\s*(\\d+)");
            java.util.regex.Pattern pr = java.util.regex.Pattern.compile("\"rule_name\"\\s*:\\s*\"([^\"]+)\"");
            Integer done = null, total = null;
            String stage = null;
            for (int i = from; i < lines.size(); i++) {
                String line = lines.get(i);
                java.util.regex.Matcher mj = pj.matcher(line);
                java.util.regex.Matcher mt = pt.matcher(line);
                if (mj.find() && mt.find()) {
                    done = Integer.parseInt(mj.group(1));
                    total = Integer.parseInt(mt.group(1));
                }
                java.util.regex.Matcher mr = pr.matcher(line);
                if (mr.find()) {
                    stage = mr.group(1);
                }
            }
            if (done == null || total == null || total <= 0) {
                return null;
            }
            int pct = (int) Math.min(99, Math.round(100.0 * done / total));
            String t = done + "/" + total + (stage != null ? " · " + stage : "");
            return new ProgressInfo(pct, t);
        } catch (Exception ex) {
            return null;
        }
    }

    /** A 层:snakemake 控制台文本("N of M steps (P%) done" + 最新规则行) */
    private ProgressInfo parseSnakemakeText(String text, String status) {
        if (text == null || text.isBlank()) {
            return new ProgressInfo(null, "RUNNING".equals(status) ? "准备中(DAG 构建)" : null);
        }
        java.util.regex.Matcher steps = java.util.regex.Pattern
                .compile("(\\d+) of (\\d+) steps \\((\\d+)%\\) done").matcher(text);
        Integer pct = null;
        String stepsText = null;
        while (steps.find()) {
            pct = Integer.parseInt(steps.group(3));
            stepsText = steps.group(1) + "/" + steps.group(2);
        }
        java.util.regex.Matcher rule = java.util.regex.Pattern
                .compile("(?m)^\\s*(?:local)?rule (\\w+):").matcher(text);
        String stage = null;
        while (rule.find()) {
            stage = rule.group(1);
        }
        if (stepsText != null) {
            return new ProgressInfo(pct, stepsText + (stage != null ? " · " + stage : ""));
        }
        // 兜底:通用 "N of M"(非 snakemake 输出)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s+of\\s+(\\d+)").matcher(text);
        int a = -1, b = -1;
        while (m.find()) {
            int x = Integer.parseInt(m.group(1));
            int y = Integer.parseInt(m.group(2));
            if (y > 0 && x >= 0 && x <= y) {
                a = x;
                b = y;
            }
        }
        if (a >= 0 && b > 0) {
            return new ProgressInfo((int) Math.min(99, Math.round(100.0 * a / b)), a + "/" + b);
        }
        return new ProgressInfo(null, "RUNNING".equals(status) ? "准备中(DAG 构建)" : null);
    }

    /** 输出目录下的固定名文件(如 progress.jsonl) */
    private java.nio.file.Path resolveUnderOutput(PipelineExecution e, String name) {
        if (e.getOutputPath() == null || e.getOutputPath().isBlank()) {
            return null;
        }
        for (String root : outputRoots()) {
            try {
                java.nio.file.Path f = com.bioplatform.common.util.HostPathUtil.resolveExisting(
                        root + "/" + e.getOutputPath() + "/" + name);
                if (f != null && java.nio.file.Files.exists(f)) {
                    return f;
                }
            } catch (Exception ignored) {
                // 该根下没有,试下一个
            }
        }
        return null;
    }

    /** 输出目录下最新的 snakemake 控制台日志(<工作目录>/.snakemake/log/*.log) */
    private java.nio.file.Path newestSnakemakeLog(PipelineExecution e) {
        if (e.getOutputPath() == null || e.getOutputPath().isBlank()) {
            return null;
        }
        java.nio.file.Path best = null;
        java.time.Instant bestTime = java.time.Instant.EPOCH;
        for (String root : outputRoots()) {
            try {
                java.nio.file.Path dir = com.bioplatform.common.util.HostPathUtil.resolveExisting(
                        root + "/" + e.getOutputPath());
                if (!java.nio.file.Files.isDirectory(dir)) {
                    continue;
                }
                try (var stream = java.nio.file.Files.walk(dir, 6)) {
                    for (java.nio.file.Path p : stream.filter(java.nio.file.Files::isRegularFile).toList()) {
                        String s = p.toString().replace('\\', '/');
                        if (s.contains("/.snakemake/log/") && s.endsWith(".log")) {
                            java.time.Instant t = java.nio.file.Files.getLastModifiedTime(p).toInstant();
                            if (t.isAfter(bestTime)) {
                                bestTime = t;
                                best = p;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // 该根下不可读,试下一个
            }
        }
        return best;
    }

    private List<String> outputRoots() {
        List<String> roots = new java.util.ArrayList<>();
        String outRoot = systemService.getConfigValue("execution.outputRoot");
        if (outRoot != null && !outRoot.isBlank()) {
            roots.add(outRoot.trim());
        }
        if (dataRoot != null && !dataRoot.isBlank()) {
            roots.add(dataRoot);
        }
        roots.add(sharedPath);
        return roots;
    }

    /** 日志尾部片段(不整读大文件) */
    private String tailOf(java.nio.file.Path f, int maxChars) {
        if (f == null) {
            return null;
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(f);
            int from = Math.max(0, bytes.length - maxChars);
            return new String(bytes, from, bytes.length - from, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 定位 task_output.log:输出根目录(system_configs execution.outputRoot,可改)优先,
     * 其次上传卷宿主机路径,最后卷内路径(历史数据)。根目录可配置后日志在根目录下,
     * 只认卷路径会让执行监控"没信息"。
     */
    private java.nio.file.Path resolveTaskLog(PipelineExecution e) {
        if (e.getOutputPath() == null || e.getOutputPath().isBlank()) {
            return null;
        }
        java.util.List<String> roots = new java.util.ArrayList<>();
        String outRoot = systemService.getConfigValue("execution.outputRoot");
        if (outRoot != null && !outRoot.isBlank()) {
            roots.add(outRoot.trim());
        }
        if (dataRoot != null && !dataRoot.isBlank()) {
            roots.add(dataRoot);
        }
        roots.add(sharedPath);
        for (String root : roots) {
            for (String name : new String[]{"task_output." + e.getId() + ".log", "task_output.log"}) {
                try {
                    java.nio.file.Path f = com.bioplatform.common.util.HostPathUtil.resolveExisting(
                            root + "/" + e.getOutputPath() + "/" + name);
                    if (f != null && java.nio.file.Files.exists(f)) {
                        return f;
                    }
                } catch (Exception ignored) {
                    // 该根下没有,试下一个
                }
            }
        }
        return null;
    }

    /** 执行输出:task_output.log(实时 tee)→ worker 实时输出 → errorLog(兜底) */
    private String resolveLogText(PipelineExecution e) {
        java.nio.file.Path f = resolveTaskLog(e);
        if (f != null) {
            try {
                String text = java.nio.file.Files.readString(f);
                return text.length() > 200_000 ? text.substring(text.length() - 200_000) : text;
            } catch (Exception ignored) {
                // 落盘读不到时继续兜底
            }
        }
        String live = taskDispatcher.fetchTaskOutput(e);
        if (live != null && !live.isBlank()) {
            return live;
        }
        return e.getErrorLog() == null || e.getErrorLog().isBlank()
                ? "执行状态: " + e.getStatus() + "\n暂无详细日志"
                : e.getErrorLog();
    }

    @Override
    public void cancelExecution(Long id) {
        PipelineExecution execution = pipelineExecutionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }

        // 委托 dispatcher:RUNNING 任务会真杀执行端进程(旧版只改 DB 是假取消)
        boolean cancelled = taskDispatcher.cancelExecution(id);
        if (!cancelled) {
            throw new IllegalArgumentException("当前状态不允许取消执行");
        }

        log.info("取消执行成功: executionId={}", id);
    }

    @Override
    public String getExecutionLogs(Long id) {
        PipelineExecution execution = pipelineExecutionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }
        // 真实执行输出(task_output.log → worker 实时 → errorLog)
        return resolveLogText(execution);
    }
}
