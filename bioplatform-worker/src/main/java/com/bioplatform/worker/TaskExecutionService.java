package com.bioplatform.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行服务
 * 接收任务，异步执行本地命令
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    /** taskId → Task */
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();

    /**
     * 提交任务
     */
    public Task submitTask(Long executionId, Long pipelineId, String command, String inputParams) {
        Task task = new Task();
        task.setId(UUID.randomUUID().toString().substring(0, 8));
        task.setExecutionId(executionId);
        task.setPipelineId(pipelineId);
        task.setCommand(command);
        task.setInputParams(inputParams);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        tasks.put(task.getId(), task);

        log.info("任务已接收: taskId={}, executionId={}, command={}", task.getId(), executionId, command);

        // 异步执行
        executeAsync(task.getId());

        return task;
    }

    /**
     * 异步执行任务
     */
    @Async
    public void executeAsync(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) return;

        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());

        log.info("任务开始执行: taskId={}", taskId);

        try {
            // 解析命令：支持 shell 命令或工具调用
            String command = task.getCommand();
            if (command == null || command.isBlank()) {
                // 模拟执行（开发测试用）
                simulateExecution(task);
            } else {
                // 实际执行命令
                executeCommand(task, command);
            }
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setError(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            log.error("任务执行失败: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    /**
     * 模拟执行（开发测试）
     */
    private void simulateExecution(Task task) throws InterruptedException {
        log.info("模拟执行任务: taskId={}", task.getId());
        // 模拟执行耗时
        Thread.sleep(5000);
        task.setStatus("COMPLETED");
        task.setOutput("模拟执行完成。输入参数: " + task.getInputParams());
        task.setFinishedAt(LocalDateTime.now());
        log.info("模拟执行完成: taskId={}", task.getId());
    }

    /**
     * 执行实际命令
     */
    private void executeCommand(Task task, String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        task.setFinishedAt(LocalDateTime.now());

        if (exitCode == 0) {
            task.setStatus("COMPLETED");
            task.setOutput(output.toString());
            log.info("命令执行成功: taskId={}, exitCode={}", task.getId(), exitCode);
        } else {
            task.setStatus("FAILED");
            task.setError("Exit code: " + exitCode + "\n" + output);
            log.warn("命令执行失败: taskId={}, exitCode={}", task.getId(), exitCode);
        }
    }

    /**
     * 获取任务
     */
    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null || "COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return false;
        }
        task.setStatus("CANCELLED");
        task.setFinishedAt(LocalDateTime.now());
        return true;
    }

    /**
     * 同步执行 shell 命令（Agent 工具调用专用）
     * 阻塞等待命令执行完成，返回结果 Map
     *
     * @param command 要执行的命令
     * @param timeout 超时秒数
     * @param workdir 工作目录（可为 null）
     * @return 包含 exit_code, success, output 的 Map
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
}
