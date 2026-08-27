package com.bioplatform.worker;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Worker REST API
 * 供 Gateway（公网服务器）调用
 */
@RestController
@RequestMapping("/worker")
public class WorkerController {

    private final TaskExecutionService taskService;

    public WorkerController(TaskExecutionService taskService) {
        this.taskService = taskService;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Runtime rt = Runtime.getRuntime();
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        long totalMemGB = os.getTotalPhysicalMemorySize() / 1024 / 1024 / 1024;
        long freeMemGB = os.getFreePhysicalMemorySize() / 1024 / 1024 / 1024;
        return Map.of(
                "status", "UP",
                "hostname", getHostname(),
                "cpuCores", rt.availableProcessors(),
                "freeMemoryGB", freeMemGB,
                "totalMemoryGB", totalMemGB,
                "tools", listTools()
        );
    }

    /**
     * 提交任务
     */
    @PostMapping("/tasks/submit")
    public Map<String, Object> submitTask(@RequestBody Map<String, Object> params) {
        Long executionId = Long.valueOf(params.get("executionId").toString());
        Long pipelineId = params.get("pipelineId") != null ? Long.valueOf(params.get("pipelineId").toString()) : null;
        String command = params.getOrDefault("command", "").toString();
        String inputParams = params.getOrDefault("inputParams", "{}").toString();

        Task task = taskService.submitTask(executionId, pipelineId, command, inputParams);
        return Map.of("taskId", task.getId(), "status", task.getStatus());
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/tasks/{taskId}/status")
    public Map<String, Object> taskStatus(@PathVariable String taskId) {
        Task task = taskService.getTask(taskId);
        if (task == null) {
            return Map.of("status", "NOT_FOUND");
        }
        return Map.of("status", task.getStatus());
    }

    /**
     * 获取任务输出
     */
    @GetMapping("/tasks/{taskId}/output")
    public Map<String, Object> taskOutput(@PathVariable String taskId) {
        Task task = taskService.getTask(taskId);
        if (task == null) {
            return Map.of("output", "", "error", "Task not found");
        }
        return Map.of(
                "status", task.getStatus(),
                "output", task.getOutput() != null ? task.getOutput() : "",
                "error", task.getError() != null ? task.getError() : ""
        );
    }

    /**
     * 取消任务
     */
    @PostMapping("/tasks/{taskId}/cancel")
    public Map<String, Object> cancelTask(@PathVariable String taskId) {
        boolean cancelled = taskService.cancelTask(taskId);
        return Map.of("cancelled", cancelled);
    }

    /**
     * 列出所有任务
     */
    @GetMapping("/tasks")
    public List<Task> listTasks() {
        return List.of(); // 简化实现
    }

    private String getHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private List<String> listTools() {
        // 检查本地安装的生信工具
        return List.of("blast", "fastqc", "samtools", "bedtools");
    }
}
