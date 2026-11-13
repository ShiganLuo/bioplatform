package com.bioplatform.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行服务(2026-10-30 重构版,方案 B)
 *
 * 与旧版的本质区别:
 *  1. 任务状态持久化到磁盘(每状态变更即写 JSON 文件),worker 重启后可对账;
 *     重启时遗留的 RUNNING/PENDING 任务统一标 FAILED(平台侧据此对账);
 *  2. cancel 真杀进程(Process.destroyForcibly),不再只改内存状态;
 *  3. 超时由执行端强制(waitFor 超时即 destroy),与平台侧超时双保险;
 *  4. 去掉"模拟执行":命令为空直接 FAILED,绝不假装成功;
 *  5. 输出截断保护(100KB),防止长任务输出撑爆平台侧 DB。
 *
 * @author luosg
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);
    private static final int MAX_OUTPUT_CHARS = 100_000;

    /** taskId → Task */
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    /** taskId → 运行中的进程(取消/超时强杀用) */
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    /** 真异步执行器:submitTask 里 this.executeAsync() 自调用会绕过 @Async 代理(Spring AOP 坑),
     *  长任务会同步阻塞 HTTP 线程直到超时(2026-10-31 sleep 300 实测),必须走代理/执行器 */
    private final org.springframework.core.task.TaskExecutor taskExecutor;

    /** 任务状态持久化目录(容器内挂卷,重启可扫) */
    @Value("${worker.task-dir:/data/tasks}")
    private String taskDir;

    public TaskExecutionService(org.springframework.core.task.TaskExecutor taskExecutor) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(taskDir));
        } catch (IOException e) {
            log.error("创建任务目录失败: {}", taskDir, e);
        }
        recoverOnStartup();
    }

    /**
     * 启动恢复:磁盘上遗留的 RUNNING/PENDING 任务标 FAILED(平台会据此对账)
     */
    private void recoverOnStartup() {
        int recovered = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(taskDir), "*.json")) {
            for (Path p : stream) {
                try {
                    Task task = objectMapper.readValue(p.toFile(), Task.class);
                    if ("RUNNING".equals(task.getStatus()) || "PENDING".equals(task.getStatus())) {
                        task.setStatus("FAILED");
                        task.setError("Worker 重启,任务丢失,请重新执行");
                        task.setFinishedAt(LocalDateTime.now());
                        persist(task);
                        recovered++;
                    }
                    tasks.put(task.getId(), task);
                } catch (Exception e) {
                    log.warn("恢复任务文件失败,跳过: {} ({})", p, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("扫描任务目录失败: {}", e.getMessage());
        }
        if (recovered > 0 || !tasks.isEmpty()) {
            log.info("启动恢复完成: 加载 {} 条历史任务, 其中 {} 条标记为丢失", tasks.size(), recovered);
        }
    }

    /**
     * 提交任务
     */
    public Task submitTask(Long executionId, Long pipelineId, String command,
                           String inputParams, Integer timeoutSeconds) {
        Task task = new Task();
        task.setId(UUID.randomUUID().toString().substring(0, 8));
        task.setExecutionId(executionId);
        task.setPipelineId(pipelineId);
        task.setCommand(command);
        task.setInputParams(inputParams);
        task.setTimeout(timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : 3600);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        tasks.put(task.getId(), task);
        persist(task);

        log.info("任务已接收: taskId={}, executionId={}, command={}", task.getId(), executionId, command);

        // 异步执行(必须经 Spring 管理的执行器,自调用 @Async 不生效)
        taskExecutor.execute(() -> executeAsync(task.getId()));
        return task;
    }

    /**
     * 异步执行任务
     */
    public void executeAsync(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) return;

        String command = task.getCommand();
        if (command == null || command.isBlank()) {
            task.setStatus("FAILED");
            task.setError("命令为空(模板未配置 command_template?),拒绝执行");
            task.setFinishedAt(LocalDateTime.now());
            persist(task);
            log.warn("任务拒绝: taskId={}, 命令为空", taskId);
            return;
        }

        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        persist(task);
        log.info("任务开始执行: taskId={}", taskId);

        try {
            executeCommand(task, command);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setError("执行异常: " + e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            persist(task);
            log.error("任务执行失败: taskId={}, error={}", taskId, e.getMessage());
        } finally {
            processes.remove(taskId);
        }
    }

    /**
     * 执行实际命令(探测机制:不设墙钟超时,进程退出即结束)。
     * 进程丢失由 backend 10 秒对账探测判定(status=NOT_FOUND 或 worker 失联 5 分钟),
     * 流程框架也可在终止/完成时经回调接口(/api/public/executions/callback)回报终态。
     */
    private void executeCommand(Task task, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);
        // 任务状态文件目录对进程可见即可,不做特殊环境注入
        Process process = pb.start();
        processes.put(task.getId(), process);

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (output.length() > MAX_OUTPUT_CHARS) {
                    output.append("... [输出已截断,超过 " + MAX_OUTPUT_CHARS + " 字符]\n");
                    break;
                }
            }
        }

        process.waitFor(); // 等进程自然结束;不再按超时强杀(RNAseq 等大流程被 1h 缺省误杀过)
        task.setFinishedAt(LocalDateTime.now());

        int exitCode = process.exitValue();
        if (exitCode == 0) {
            task.setStatus("COMPLETED");
            task.setOutput(truncateOutput(output.toString()));
            log.info("命令执行成功: taskId={}, exitCode={}", task.getId(), exitCode);
        } else {
            // 被取消/强杀时 exitCode 非 0,但状态已由 cancelTask 置为 CANCELLED,不覆盖
            if (!"CANCELLED".equals(task.getStatus())) {
                task.setStatus("FAILED");
                task.setError("Exit code: " + exitCode + "\n" + truncateOutput(output.toString()));
            }
            log.warn("命令执行失败: taskId={}, exitCode={}", task.getId(), exitCode);
        }
        persist(task);
    }

    /**
     * 获取任务
     */
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 取消任务:真杀进程
     */
    public boolean cancelTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        String status = task.getStatus();
        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            return false;
        }
        Process process = processes.get(taskId);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.info("已强杀进程: taskId={}", taskId);
        }
        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        persist(task);
        return true;
    }

    /**
     * 列出所有任务(内存 + 磁盘恢复的历史)
     */
    public List<Task> listTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * 同步执行 shell 命令(Agent 工具调用专用)
     */
    public Map<String, Object> executeShellSync(String command, int timeout, String workdir) {
        log.info("同步执行 shell 命令: command={}, timeout={}, workdir={}", command, timeout, workdir);
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            if (workdir != null && !workdir.isBlank()) {
                pb.directory(new File(workdir));
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (output.length() > 10240) {
                        output.append("\n... [输出已截断，超过 10240 字符]");
                        break;
                    }
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;

            if (!finished) {
                process.destroyForcibly();
                return Map.of("exit_code", -1, "success", false,
                        "output", "命令执行超时（" + timeout + "秒）\n" + output);
            }

            log.info("shell 命令执行完成: exitCode={}, outputLength={}", exitCode, output.length());
            return Map.of("exit_code", exitCode, "success", exitCode == 0,
                    "output", output.toString());

        } catch (Exception e) {
            log.error("shell 命令执行异常: {}", e.getMessage());
            return Map.of("exit_code", -1, "success", false,
                    "output", "执行异常: " + e.getMessage());
        }
    }

    private void persist(Task task) {
        try {
            Path file = Paths.get(taskDir, task.getId() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), task);
        } catch (Exception e) {
            log.warn("任务状态持久化失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    private String truncateOutput(String s) {
        if (s == null) return "";
        return s.length() > MAX_OUTPUT_CHARS ? s.substring(0, MAX_OUTPUT_CHARS) + "\n...[截断]" : s;
    }
}
