package com.bioplatform.service;

import com.bioplatform.common.util.AesEncryptUtil;
import com.bioplatform.common.util.HostPathUtil;
import com.bioplatform.entity.SystemConfig;
import com.bioplatform.mapper.SystemConfigMapper;
import com.bioplatform.worker.HttpUtil;
import com.bioplatform.worker.WorkerRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 节点状态指标服务（仪表盘"节点状态"卡 / 计算节点管理负载列 / 主机监控页共用）
 *
 * 口径（2026-11-23 节点状态展示改造方案）：
 * - 应用节点 CPU/内存：宿主机 /host/proc 优先，自动降级容器视图（metricNote 标注）
 * - 磁盘：真实路径 df 水位（(总量-可用)/总量，含保留块，对容量风险更保守）
 * - 计算节点：实时拉 worker /worker/metrics，2s 超时、3s 兜底，失败降级不拖垮整体
 */
@Service
public class NodeMetricsService {

    private static final Logger log = LoggerFactory.getLogger(NodeMetricsService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WorkerRegistry workerRegistry;
    private final SystemConfigMapper systemConfigMapper;

    /** Worker 端点鉴权 token（与 WorkerRegistry 同源配置） */
    @Value("${bioplatform.worker.token:}")
    private String workerToken;

    /** 共享存储根（应用节点磁盘水位探测点之一） */
    @Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    public NodeMetricsService(WorkerRegistry workerRegistry, SystemConfigMapper systemConfigMapper) {
        this.workerRegistry = workerRegistry;
        this.systemConfigMapper = systemConfigMapper;
    }

    /**
     * 组装节点卡片：应用节点自采 + 计算节点并行拉 /worker/metrics。
     * 单节点失败只降级该卡片（online=false，指标 null），绝不影响整体响应。
     */
    public List<Map<String, Object>> buildNodeCards() {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(appNodeCard());
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (WorkerRegistry.WorkerInfo w : workerRegistry.getAllWorkers()) {
            if (w.getStatus() != 1) {
                Map<String, Object> card = computeCardShell(w);
                card.put("online", false);
                card.put("metricNote", "已禁用");
                cards.add(card);
                continue;
            }
            futures.add(CompletableFuture
                    .supplyAsync(() -> fetchWorkerCard(w))
                    .orTimeout(3, TimeUnit.SECONDS)
                    .exceptionally(ex -> offlineComputeCard(w)));
        }
        for (CompletableFuture<Map<String, Object>> f : futures) {
            cards.add(f.join());
        }
        return cards;
    }

    /** 应用节点卡：CPU/内存口径自动探测（宿主机 /host/proc 优先，降级容器视图），磁盘=真实 df 水位 */
    private Map<String, Object> appNodeCard() {
        boolean hostView = java.nio.file.Files.isReadable(java.nio.file.Paths.get("/host/proc/meminfo"));
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("role", "APP");
        card.put("nodeId", "app-backend");
        card.put("hostname", localHostname());
        card.put("online", true);
        card.put("cpuUsage", hostView ? hostCpuUsagePercent() : containerCpuUsagePercent());
        card.put("memoryUsage", hostView ? hostMemoryUsagePercent() : containerMemoryUsagePercent());
        card.put("diskUsage", appDiskUsagePercent());
        card.put("runningTasks", null);
        card.put("uptime", formatUptime(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()));
        card.put("version", appVersion());
        card.put("metricNote", hostView ? "宿主机视图" : "容器视图");
        return card;
    }

    /** 计算节点卡：实时拉 worker /worker/metrics（2s 超时） */
    private Map<String, Object> fetchWorkerCard(WorkerRegistry.WorkerInfo w) {
        try {
            String body = HttpUtil.get(w.getUrl() + "/worker/metrics", authHeaders(), java.time.Duration.ofSeconds(2));
            JsonNode n = JSON.readTree(body);
            Map<String, Object> card = computeCardShell(w);
            card.put("online", true);
            card.put("hostname", n.path("hostname").asText(w.getHostname()));
            card.put("cpuUsage", jsonPercent(n, "cpuUsage"));
            card.put("memoryUsage", jsonPercent(n, "memoryUsage"));
            card.put("diskUsage", jsonPercent(n, "diskUsage"));
            JsonNode rt = n.path("runningTasks");
            card.put("runningTasks", rt.isNumber() ? rt.asLong() : null);
            card.put("uptime", n.path("uptime").isTextual() ? n.path("uptime").asText() : null);
            card.put("metricNote", "宿主机视图");
            return card;
        } catch (Exception e) {
            log.warn("拉取计算节点指标失败: id={}, url={}, error={}", w.getId(), w.getUrl(), e.getMessage());
            return offlineComputeCard(w);
        }
    }

    private Map<String, Object> computeCardShell(WorkerRegistry.WorkerInfo w) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("role", "COMPUTE");
        card.put("nodeId", w.getId());
        card.put("hostname", w.getHostname());
        card.put("online", true);
        card.put("cpuUsage", null);
        card.put("memoryUsage", null);
        card.put("diskUsage", null);
        card.put("runningTasks", null);
        card.put("uptime", null);
        card.put("metricNote", "");
        return card;
    }

    private Map<String, Object> offlineComputeCard(WorkerRegistry.WorkerInfo w) {
        Map<String, Object> card = computeCardShell(w);
        card.put("online", false);
        card.put("metricNote", "采集失败（离线或超时）");
        return card;
    }

    private Map<String, String> authHeaders() {
        return workerToken == null || workerToken.isBlank()
                ? Map.of() : Map.of("X-Worker-Token", workerToken);
    }

    private Double jsonPercent(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isNumber() ? clampPercent(v.asDouble()) : null;
    }

    /** 宿主机 CPU 使用率%：/host/proc/stat 两次采样（500ms）差分；不可得回退容器视图 */
    private Double hostCpuUsagePercent() {
        try {
            long[] a = readProcStatTotals("/host/proc/stat");
            Thread.sleep(500);
            long[] b = readProcStatTotals("/host/proc/stat");
            long total = b[0] - a[0];
            long idle = b[1] - a[1];
            return total <= 0 ? null : clampPercent((1.0 - (double) idle / total) * 100.0);
        } catch (Exception e) {
            return containerCpuUsagePercent();
        }
    }

    private long[] readProcStatTotals(String path) throws java.io.IOException {
        try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(path))) {
            String[] p = r.readLine().trim().split("\\s+");
            long total = 0;
            for (int i = 1; i < p.length; i++) {
                total += Long.parseLong(p[i]);
            }
            long idle = Long.parseLong(p[4]) + (p.length > 5 ? Long.parseLong(p[5]) : 0);
            return new long[]{total, idle};
        }
    }

    /** 宿主机内存使用率%：/host/proc/meminfo；不可得回退容器视图 */
    private Double hostMemoryUsagePercent() {
        try (java.io.BufferedReader r = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get("/host/proc/meminfo"))) {
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
            // 落入容器视图兜底
        }
        return containerMemoryUsagePercent();
    }

    private Double containerCpuUsagePercent() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            double load = os.getSystemCpuLoad() * 100.0;
            return load < 0 ? null : clampPercent(load);
        } catch (Exception e) {
            return null;
        }
    }

    private Double containerMemoryUsagePercent() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long total = os.getTotalPhysicalMemorySize();
            if (total > 0) {
                return clampPercent((1.0 - (double) os.getFreePhysicalMemorySize() / total) * 100.0);
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

    /** 应用节点磁盘水位%：共享存储根 与 执行输出根（execution.outputRoot，经 HostPathUtil 解析）取较高者 */
    private Double appDiskUsagePercent() {
        List<String> paths = new ArrayList<>();
        paths.add(sharedPath);
        try {
            String outputRoot = configValue("execution.outputRoot");
            if (outputRoot != null && !outputRoot.isBlank()) {
                paths.add(HostPathUtil.resolveExisting(outputRoot).toString());
            }
        } catch (Exception ignored) {
            // 输出根未配置或解析失败，只看共享存储
        }
        Double highest = null;
        for (String p : paths) {
            try {
                java.nio.file.FileStore store = java.nio.file.Files.getFileStore(java.nio.file.Paths.get(p));
                long total = store.getTotalSpace();
                if (total > 0) {
                    double usage = (1.0 - (double) store.getUsableSpace() / total) * 100.0;
                    Double clamped = clampPercent(usage);
                    if (highest == null || clamped > highest) {
                        highest = clamped;
                    }
                }
            } catch (Exception ignored) {
                // 该路径探测不到，看下一个
            }
        }
        return highest;
    }

    private String configValue(String key) {
        SystemConfig config = systemConfigMapper.selectByKey(key);
        return config == null ? null : AesEncryptUtil.decrypt(config.getConfigValue());
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

    private String localHostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String appVersion() {
        Package pkg = getClass().getPackage();
        String v = pkg != null ? pkg.getImplementationVersion() : null;
        return v != null ? v : "dev";
    }
}
