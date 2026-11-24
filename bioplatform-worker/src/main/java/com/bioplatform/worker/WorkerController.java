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
     * 节点资源指标（仪表盘"节点状态"用）
     * 本 worker privileged + pid:host 运行,/proc/stat、/proc/meminfo 即宿主机视图。
     * 指标不可得时返回 null(前端显示 N/A,不用 0 伪装"空闲")。
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("hostname", getHostname());
        m.put("cpuUsage", cpuUsagePercent());
        m.put("memoryUsage", memoryUsagePercent());
        m.put("diskUsage", diskUsagePercent());
        m.put("runningTasks", taskService.countRunning());
        m.put("uptime", formatUptime(
                java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()));
        return m;
    }

    /** CPU 使用率%: /proc/stat 两次采样(500ms)差分;失败回退 MXBean;均失败返回 null */
    private Double cpuUsagePercent() {
        try {
            long[] a = readProcStatTotals();
            Thread.sleep(500);
            long[] b = readProcStatTotals();
            long total = b[0] - a[0];
            long idle = b[1] - a[1];
            if (total <= 0) {
                return null;
            }
            return clampPercent((1.0 - (double) idle / total) * 100.0);
        } catch (Exception e) {
            try {
                com.sun.management.OperatingSystemMXBean os =
                        (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                double load = os.getSystemCpuLoad() * 100.0;
                return load < 0 ? null : clampPercent(load);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /** /proc/stat 首行 "cpu ..." 全核汇总 → [total, idle(含 iowait)] */
    private long[] readProcStatTotals() throws java.io.IOException {
        try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("/proc/stat"))) {
            String[] p = r.readLine().trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < p.length; i++) {
                total += Long.parseLong(p[i]);
            }
            long idle = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0);
            return new long[]{total, idle};
        }
    }

    /** 内存使用率%: /proc/meminfo MemTotal/MemAvailable;失败回退 MXBean */
    private Double memoryUsagePercent() {
        try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of("/proc/meminfo"))) {
            long total = -1;
            long available = -1;
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = parseMeminfoKb(line);
                } else if (line.startsWith("MemAvailable:")) {
                    available = parseMeminfoKb(line);
                }
                if (total > 0 && available >= 0) {
                    break;
                }
            }
            if (total > 0 && available >= 0) {
                return clampPercent((1.0 - (double) available / total) * 100.0);
            }
        } catch (Exception ignored) {
            // 落入 MXBean 兜底
        }
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalPhysicalMemorySize();
            if (total > 0) {
                long free = os.getFreePhysicalMemorySize();
                return clampPercent((1.0 - (double) free / total) * 100.0);
            }
        } catch (Exception ignored) {
            // 不可得
        }
        return null;
    }

    private long parseMeminfoKb(String line) {
        String[] p = line.split("\\s+");
        return p.length > 1 ? Long.parseLong(p[1]) : -1;
    }

    /** 磁盘使用率%: 任务数据盘(上传卷)→任务状态卷→根,首个可探测的文件系统 */
    private Double diskUsagePercent() {
        for (String p : new String[]{"/app/uploads", "/data/tasks", "/"}) {
            try {
                java.nio.file.FileStore store = java.nio.file.Files.getFileStore(java.nio.file.Path.of(p));
                long total = store.getTotalSpace();
                if (total > 0) {
                    long usable = store.getUsableSpace();
                    return clampPercent((1.0 - (double) usable / total) * 100.0);
                }
            } catch (Exception ignored) {
                // 换下一个路径
            }
        }
        return null;
    }

    private Double clampPercent(double value) {
        return Math.round(Math.max(0, Math.min(100, value)) * 10.0) / 10.0;
    }

    private String formatUptime(long millis) {
        long minutes = millis / 60000;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;
        return days > 0 ? (days + "d " + hours + "h " + mins + "m") : (hours + "h " + mins + "m");
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
        Integer timeout = params.containsKey("timeout")
                ? Integer.parseInt(params.get("timeout").toString()) : null;

        Task task = taskService.submitTask(executionId, pipelineId, command, inputParams, timeout);
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
        return taskService.listTasks();
    }

    /**
     * 同步执行 shell 命令（Agent 工具调用）
     * 直接在宿主机上执行，返回结果
     */
    @PostMapping("/shell/execute")
    public Map<String, Object> shellExecute(@RequestBody Map<String, Object> params) {
        String command = params.getOrDefault("command", "").toString();
        int timeout = 30;
        if (params.containsKey("timeout")) {
            try {
                timeout = Integer.parseInt(params.get("timeout").toString());
            } catch (NumberFormatException ignored) {}
        }
        timeout = Math.min(timeout, 300);

        String workdir = params.containsKey("workdir") ? params.get("workdir").toString() : null;

        if (command.isBlank()) {
            return Map.of("exit_code", -1, "success", false, "output", "命令不能为空");
        }

        return taskService.executeShellSync(command, timeout, workdir);
    }

    private String getHostname() {
        // 优先宿主机真实主机名：pid: host 下 /proc/1 即宿主机 init，经其 root 视图读
        // /etc/hostname（2026-11-24 方案C：/:/host 挂载已摘，不再借道 /host）；
        // 容器 ID 主机名无意义
        try {
            String name = java.nio.file.Files.readString(
                    java.nio.file.Path.of("/proc/1/root/etc/hostname")).trim();
            if (!name.isEmpty()) {
                return name;
            }
        } catch (Exception ignored) {
            // 无宿主机 PID 命名空间（本地开发），用 JVM 主机名
        }
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
