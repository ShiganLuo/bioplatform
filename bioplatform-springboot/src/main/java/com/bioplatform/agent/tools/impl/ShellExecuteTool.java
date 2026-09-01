package com.bioplatform.agent.tools.impl;

import com.bioplatform.agent.tools.Tool;
import com.bioplatform.worker.WorkerClient;
import com.bioplatform.worker.WorkerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Shell 执行工具 - 让 Agent 具备服务器 shell 命令执行能力
 * <p>
 * 执行策略：
 * 1. 默认在宿主机上执行（通过 chroot /host 访问宿主机的文件系统和工具）
 * 2. 指定 worker_id 时转发到远程 Worker 节点执行（NFS/跨网络场景）
 * </p>
 *
 * @author luosg
 */
@Component
public class ShellExecuteTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ShellExecuteTool.class);

    /** 输出最大字符数，防止撑爆 LLM 上下文 */
    private static final int MAX_OUTPUT_CHARS = 10240;

    /** 默认超时秒数 */
    private static final int DEFAULT_TIMEOUT = 30;

    /** 最大超时秒数 */
    private static final int MAX_TIMEOUT = 300;

    /** 是否在容器内运行（/host 目录存在说明挂载了宿主机根目录） */
    private final boolean inContainer;

    /** 容器内执行时的 chroot 前缀 */
    private static final String[] CHROOT_PREFIX = {"chroot", "/host", "bash", "-c"};

    /** 本地直接执行前缀 */
    private static final String[] LOCAL_PREFIX = {"bash", "-c"};

    /** 危险命令黑名单 */
    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("\\brm\\s+(-[a-zA-Z]*\\s+)*-?rf\\b"),
            Pattern.compile("\\bmkfs\\b"),
            Pattern.compile("\\bdd\\s+if="),
            Pattern.compile("\\bshutdown\\b"),
            Pattern.compile("\\breboot\\b"),
            Pattern.compile("\\bchmod\\s+777\\b"),
            Pattern.compile("\\b>\\s*/dev/sd"),
            Pattern.compile("\\bmkswap\\b"),
            Pattern.compile("\\b:\\(\\)\\{"),
            Pattern.compile("\\bcurl\\b.*\\|\\s*bash"),
            Pattern.compile("\\bwget\\b.*\\|\\s*bash"),
            Pattern.compile("\\bDROP\\s+DATABASE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDROP\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDELETE\\s+FROM\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bTRUNCATE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bUPDATE\\b.*\\bSET\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bINSERT\\s+INTO\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bALTER\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bCREATE\\s+TABLE\\b", Pattern.CASE_INSENSITIVE)
    );

    private final WorkerRegistry workerRegistry;
    private final WorkerClient workerClient;
    private final ObjectMapper objectMapper;

    public ShellExecuteTool(WorkerRegistry workerRegistry, WorkerClient workerClient,
                            ObjectMapper objectMapper) {
        this.workerRegistry = workerRegistry;
        this.workerClient = workerClient;
        this.objectMapper = objectMapper;
        this.inContainer = new File("/host").isDirectory();
        log.info("ShellExecuteTool 初始化: inContainer={}, 执行模式={}",
                inContainer, inContainer ? "chroot /host" : "本地直接执行");
    }

    @Override
    public String getName() {
        return "shell_execute";
    }

    @Override
    public String getDescription() {
        return "在服务器上执行 shell 命令并返回结果。可访问宿主机文件系统和已安装的工具。";
    }

    @Override
    public String getTriggerDescription() {
        return "当用户需要查看文件系统、运行生信工具、检查系统状态等操作时使用此工具。";
    }

    @Override
    public String getUsageHint() {
        return "命令在宿主机上执行（通过chroot）。可用于ls/find/du查看文件、df/free/top检查系统状态。" +
                "禁止危险操作(rm -rf)和数据库写操作。查询数据库优先使用database_query工具。";
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "要执行的 shell 命令");
        properties.put("command", commandProp);

        Map<String, Object> timeoutProp = new HashMap<>();
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "超时秒数，默认30，最大300");
        properties.put("timeout", timeoutProp);

        Map<String, Object> workdirProp = new HashMap<>();
        workdirProp.put("type", "string");
        workdirProp.put("description", "工作目录，不指定则使用默认目录");
        properties.put("workdir", workdirProp);

        Map<String, Object> workerIdProp = new HashMap<>();
        workerIdProp.put("type", "string");
        workerIdProp.put("description", "目标 Worker 节点 ID，不指定则在当前宿主机上执行。" +
                "查看可用节点可使用 system_overview 工具或执行 worker_list 命令");
        properties.put("worker_id", workerIdProp);

        schema.put("properties", properties);

        List<String> required = new ArrayList<>();
        required.add("command");
        schema.put("required", required);

        return schema;
    }

    @Override
    public String execute(Map<String, String> args) {
        String command = args.get("command");
        if (command == null || command.isBlank()) {
            return toJson(-1, "缺少必需参数: command");
        }

        int timeout = parseIntOrDefault(args.get("timeout"), DEFAULT_TIMEOUT);
        timeout = Math.min(timeout, MAX_TIMEOUT);
        String workdir = args.get("workdir");
        String workerId = args.get("worker_id");

        log.info("shell_execute: command={}, timeout={}, workdir={}, workerId={}",
                command, timeout, workdir, workerId);

        try {
            // 安全校验
            validateCommand(command);

            // 路由：指定了 worker_id 则转发到远程 Worker，否则本地执行
            if (workerId != null && !workerId.isBlank()) {
                return executeOnWorker(workerId, command, timeout, workdir);
            } else {
                return executeLocal(command, timeout, workdir);
            }
        } catch (SecurityException e) {
            log.warn("shell_execute 安全校验拦截: {}", e.getMessage());
            return toJson(-1, "命令被拦截: " + e.getMessage());
        } catch (Exception e) {
            log.error("shell_execute 执行异常", e);
            return toJson(-1, "执行异常: " + e.getMessage());
        }
    }

    /**
     * 本地执行（宿主机上执行）
     * 容器内通过 chroot /host 访问宿主机环境
     */
    private String executeLocal(String command, int timeout, String workdir) {
        try {
            String[] prefix = inContainer ? CHROOT_PREFIX : LOCAL_PREFIX;
            // chroot /host 时 workdir 需要是相对于 /host 的路径
            String effectiveWorkdir = inContainer ? null : workdir;

            List<String> cmd = new ArrayList<>(Arrays.asList(prefix));
            // chroot /host bash -c "command" — 把命令和workdir合成一个字符串
            if (inContainer && workdir != null && !workdir.isBlank()) {
                cmd.add("cd " + workdir + " && " + command);
            } else {
                cmd.add(command);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (effectiveWorkdir != null && !effectiveWorkdir.isBlank()) {
                pb.directory(new File(effectiveWorkdir));
            }

            Process process = pb.start();
            String output = readProcessOutput(process, timeout);

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;

            if (!finished) {
                process.destroyForcibly();
                return toJson(-1, "命令执行超时（" + timeout + "秒）\n" + output);
            }

            return toJson(exitCode, output);
        } catch (Exception e) {
            return toJson(-1, "本地执行失败: " + e.getMessage());
        }
    }

    /**
     * 转发到远程 Worker 节点执行
     */
    private String executeOnWorker(String workerId, String command, int timeout, String workdir) {
        WorkerRegistry.WorkerInfo worker = workerRegistry.getById(workerId);
        if (worker == null) {
            // 尝试从所有健康节点中查找
            List<WorkerRegistry.WorkerInfo> workers = workerRegistry.getHealthyWorkers();
            worker = workers.stream()
                    .filter(w -> w.getId().equals(workerId) || w.getHostname().equals(workerId))
                    .findFirst()
                    .orElse(null);
        }

        if (worker == null) {
            return toJson(-1, "未找到 Worker 节点: " + workerId +
                    "。可用节点: " + listWorkerIds());
        }

        if (!worker.isHealthy()) {
            return toJson(-1, "Worker 节点不健康: " + worker.getHostname() + " (" + workerId + ")");
        }

        log.info("转发到 Worker 执行: worker={}, command={}", worker.getHostname(), command);
        Map<String, Object> result = workerClient.executeShell(
                worker.getUrl(), command, timeout, workdir);

        int exitCode = ((Number) result.getOrDefault("exit_code", -1)).intValue();
        String output = result.getOrDefault("output", "").toString();
        return toJson(exitCode, output);
    }

    /**
     * 列出可用 Worker ID
     */
    private String listWorkerIds() {
        List<WorkerRegistry.WorkerInfo> workers = workerRegistry.getHealthyWorkers();
        if (workers.isEmpty()) return "无可用节点";
        StringBuilder sb = new StringBuilder();
        for (WorkerRegistry.WorkerInfo w : workers) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(w.getId()).append("(").append(w.getHostname()).append(")");
        }
        return sb.toString();
    }

    /**
     * 安全校验：拦截危险命令
     */
    private void validateCommand(String command) {
        for (Pattern p : BLOCKED_PATTERNS) {
            if (p.matcher(command).find()) {
                throw new SecurityException("命令包含危险操作: " + command);
            }
        }
    }

    /**
     * 读取进程输出，带截断
     */
    private String readProcessOutput(Process process, int timeout) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                if (sb.length() > MAX_OUTPUT_CHARS) {
                    sb.append("\n... [输出已截断，超过 ").append(MAX_OUTPUT_CHARS).append(" 字符]");
                    break;
                }
            }
        } catch (Exception e) {
            sb.append("\n[读取输出异常: ").append(e.getMessage()).append("]");
        }
        return sb.toString();
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String toJson(int exitCode, String output) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("exit_code", exitCode);
            result.put("success", exitCode == 0);
            result.put("output", output);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"exit_code\":" + exitCode + ",\"output\":\"" + output.replace("\"", "\\\"") + "\"}";
        }
    }
}
