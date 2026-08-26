package com.bioplatform.worker;

import com.bioplatform.entity.ComputeNode;
import com.bioplatform.mapper.ComputeNodeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker 注册中心
 * 管理计算节点的注册和健康状态，数据持久化到 compute_nodes 表
 */
@Component
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    /** nodeId → WorkerInfo (内存缓存) */
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final ComputeNodeMapper nodeMapper;
    private final ObjectMapper objectMapper;

    public WorkerRegistry(ComputeNodeMapper nodeMapper, ObjectMapper objectMapper) {
        this.nodeMapper = nodeMapper;
        this.objectMapper = objectMapper;
    }

    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void init() {
        // 从数据库加载已注册的节点
        List<ComputeNode> nodes = nodeMapper.selectEnabled();
        for (ComputeNode node : nodes) {
            WorkerInfo info = fromEntity(node);
            workers.put(info.getId(), info);
            log.info("加载计算节点: id={}, url={}", info.getId(), info.getUrl());
        }
        checkAllHealth();
    }

    /**
     * 添加计算节点
     */
    public WorkerInfo addNode(String url, String hostname) {
        String nodeId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        ComputeNode entity = new ComputeNode();
        entity.setNodeId(nodeId);
        entity.setHostname(hostname != null ? hostname : "unknown");
        entity.setUrl(url);
        entity.setCpuCores(0);
        entity.setMemoryMb(0L);
        entity.setStatus(1);
        entity.setHealthy(0);
        nodeMapper.insert(entity);

        WorkerInfo info = fromEntity(entity);
        workers.put(info.getId(), info);
        log.info("新增计算节点: id={}, url={}", nodeId, url);
        return info;
    }

    /**
     * 删除计算节点
     */
    public void removeNode(String nodeId) {
        ComputeNode node = nodeMapper.selectByNodeId(nodeId);
        if (node != null) {
            nodeMapper.deleteById(node.getId());
            workers.remove(nodeId);
            log.info("删除计算节点: id={}", nodeId);
        }
    }

    /**
     * 更新节点启用/禁用
     */
    public void setNodeEnabled(String nodeId, boolean enabled) {
        ComputeNode node = nodeMapper.selectByNodeId(nodeId);
        if (node != null) {
            ComputeNode update = new ComputeNode();
            update.setId(node.getId());
            update.setStatus(enabled ? 1 : 0);
            nodeMapper.updateById(update);
            if (enabled) {
                workers.put(nodeId, fromEntity(node));
            } else {
                workers.remove(nodeId);
            }
        }
    }

    /**
     * 获取所有节点（含内存状态）
     */
    public List<WorkerInfo> getAllWorkers() {
        // 从 DB 重新加载以保证最新
        List<ComputeNode> nodes = nodeMapper.selectAll();
        List<WorkerInfo> result = new ArrayList<>();
        for (ComputeNode node : nodes) {
            WorkerInfo cached = workers.get(node.getNodeId());
            if (cached != null) {
                result.add(cached);
            } else {
                result.add(fromEntity(node));
            }
        }
        return result;
    }

    /**
     * 获取所有健康的节点
     */
    public List<WorkerInfo> getHealthyWorkers() {
        List<WorkerInfo> result = new ArrayList<>();
        for (WorkerInfo info : workers.values()) {
            if (info.isHealthy() && info.getStatus() == 1) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 根据 ID 获取节点
     */
    public WorkerInfo getById(String nodeId) {
        return workers.get(nodeId);
    }

    /**
     * 测试节点连接
     */
    public boolean testConnection(String url) {
        try {
            String response = HttpUtil.get(url + "/worker/health");
            JsonNode node = objectMapper.readTree(response);
            return "UP".equals(node.path("status").asText());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 定时健康检查：每 30 秒
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void checkAllHealth() {
        for (WorkerInfo info : workers.values()) {
            try {
                String response = HttpUtil.get(info.getUrl() + "/worker/health");
                JsonNode node = objectMapper.readTree(response);

                boolean healthy = "UP".equals(node.path("status").asText());
                int cpuCores = node.path("cpuCores").asInt(0);
                long freeMemoryMB = node.path("freeMemoryMB").asLong(0);
                String hostname = node.path("hostname").asText(info.getHostname());

                info.setHealthy(healthy);
                info.setCpuCores(cpuCores);
                info.setFreeMemoryMB(freeMemoryMB);
                info.setHostname(hostname);
                info.setLastHeartbeat(System.currentTimeMillis());

                // 更新数据库
                nodeMapper.updateHealth(info.getId(), healthy ? 1 : 0, cpuCores, freeMemoryMB);
            } catch (Exception e) {
                info.setHealthy(false);
                nodeMapper.updateHealth(info.getId(), 0, 0, 0L);
            }
        }
    }

    private WorkerInfo fromEntity(ComputeNode node) {
        WorkerInfo info = new WorkerInfo(node.getNodeId(), node.getUrl(),
                node.getHostname(), node.getCpuCores(), node.getMemoryMb());
        info.setStatus(node.getStatus());
        info.setHealthy(node.getHealthy() != null && node.getHealthy() == 1);
        info.setLastHeartbeat(node.getLastHeartbeat() != null ?
                node.getLastHeartbeat().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 0);
        return info;
    }

    /**
     * Worker 信息
     */
    public static class WorkerInfo {
        private String id;
        private String url;
        private String hostname;
        private int cpuCores;
        private long freeMemoryMB;
        private boolean healthy;
        private int status;
        private long lastHeartbeat;

        public WorkerInfo(String id, String url, String hostname, int cpuCores, long freeMemoryMB) {
            this.id = id;
            this.url = url;
            this.hostname = hostname;
            this.cpuCores = cpuCores;
            this.freeMemoryMB = freeMemoryMB;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public int getCpuCores() { return cpuCores; }
        public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }
        public long getFreeMemoryMB() { return freeMemoryMB; }
        public void setFreeMemoryMB(long freeMemoryMB) { this.freeMemoryMB = freeMemoryMB; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public int getStatus() { return status; }
        public void setStatus(int status) { this.status = status; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    }
}
