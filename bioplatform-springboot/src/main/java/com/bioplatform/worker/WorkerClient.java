package com.bioplatform.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Worker HTTP 客户端
 * 调用内网计算节点的 REST API
 *
 * @author luosg
 */
@Component
public class WorkerClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerClient.class);

    private final WorkerRegistry workerRegistry;
    private final ObjectMapper objectMapper;

    /** Worker 端点鉴权 token(为空则不带鉴权头,兼容未启用鉴权的开发环境) */
    @Value("${bioplatform.worker.token:}")
    private String workerToken;

    public WorkerClient(WorkerRegistry workerRegistry, ObjectMapper objectMapper) {
        this.workerRegistry = workerRegistry;
        this.objectMapper = objectMapper;
    }

    private Map<String, String> authHeaders() {
        return workerToken == null || workerToken.isBlank() ? Map.of() : Map.of("X-Worker-Token", workerToken);
    }

    /**
     * 向 Worker 提交执行任务
     *
     * @param workerUrl  Worker 地址
     * @param pipelineId 流水线 ID
     * @param executionId 执行记录 ID
     * @param command    执行命令
     * @param inputParams 输入参数 JSON
     * @return 任务 ID
     */
    public String submitTask(String workerUrl, Long pipelineId, Long executionId,
                              String command, String inputParams, int timeoutSeconds) {
        try {
            Map<String, Object> body = Map.of(
                    "executionId", executionId,
                    "pipelineId", pipelineId != null ? pipelineId : 0L,
                    "command", command != null ? command : "",
                    "inputParams", inputParams != null ? inputParams : "{}",
                    "timeout", timeoutSeconds
            );

            String url = workerUrl + "/worker/tasks/submit";
            String response = HttpUtil.post(url, objectMapper.writeValueAsString(body), authHeaders());

            JsonNode node = objectMapper.readTree(response);
            String taskId = node.has("taskId") ? node.get("taskId").asText() : null;
            log.info("任务已提交到 Worker: workerUrl={}, executionId={}, taskId={}", workerUrl, executionId, taskId);
            return taskId;
        } catch (Exception e) {
            log.error("提交任务到 Worker 失败: workerUrl={}, error={}", workerUrl, e.getMessage());
            throw new RuntimeException("提交任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询 Worker 上的任务状态
     * 区分确定性丢失(NOT_FOUND,原样返回)与网络级失败(抛异常,由调用方按瞬断重试)
     */
    public String queryTaskStatus(String workerUrl, String taskId) {
        try {
            String url = workerUrl + "/worker/tasks/" + taskId + "/status";
            String response = HttpUtil.get(url, authHeaders());
            JsonNode node = objectMapper.readTree(response);
            return node.has("status") ? node.get("status").asText() : "UNKNOWN";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("查询任务状态失败: " + e.getMessage(), e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("查询任务状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 Worker 上的任务输出
     */
    public String getTaskOutput(String workerUrl, String taskId) {
        try {
            String url = workerUrl + "/worker/tasks/" + taskId + "/output";
            String response = HttpUtil.get(url, authHeaders());
            JsonNode node = objectMapper.readTree(response);
            // 失败时错误详情在 error 字段,成功时在 output 字段,合并返回
            String output = node.has("output") ? node.get("output").asText() : "";
            String error = node.has("error") ? node.get("error").asText() : "";
            return error.isBlank() ? output
                    : (output.isBlank() ? error : output + "\n" + error);
        } catch (Exception e) {
            log.error("获取任务输出失败: workerUrl={}, taskId={}, error={}", workerUrl, taskId, e.getMessage());
            return "";
        }
    }

    /**
     * 取消 Worker 上的任务
     */
    public boolean cancelTask(String workerUrl, String taskId) {
        try {
            String url = workerUrl + "/worker/tasks/" + taskId + "/cancel";
            HttpUtil.post(url, "{}", authHeaders());
            return true;
        } catch (Exception e) {
            log.error("取消任务失败: workerUrl={}, taskId={}, error={}", workerUrl, taskId, e.getMessage());
            return false;
        }
    }

    /**
     * 检查 Worker 健康状态
     */
    public boolean isHealthy(String workerUrl) {
        try {
            String response = HttpUtil.get(workerUrl + "/worker/health", authHeaders());
            JsonNode node = objectMapper.readTree(response);
            return "UP".equals(node.has("status") ? node.get("status").asText() : "");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 上传文件到 Worker
     */
    public String uploadFile(String workerUrl, Long projectId, String fileName, byte[] data) {
        try {
            String url = workerUrl + "/worker/storage/upload";
            Map<String, Object> body = Map.of(
                    "projectId", projectId,
                    "fileName", fileName,
                    "data", java.util.Base64.getEncoder().encodeToString(data)
            );
            String response = HttpUtil.post(url, objectMapper.writeValueAsString(body));
            JsonNode node = objectMapper.readTree(response);
            return node.has("path") ? node.get("path").asText() : null;
        } catch (Exception e) {
            log.error("上传文件到Worker失败: {}", e.getMessage());
            throw new RuntimeException("上传文件到Worker失败", e);
        }
    }

    /**
     * 从 Worker 下载文件
     */
    public byte[] downloadFile(String workerUrl, String remotePath) {
        try {
            String url = workerUrl + "/worker/storage/download?path=" + java.net.URLEncoder.encode(remotePath, "UTF-8");
            String response = HttpUtil.get(url);
            JsonNode node = objectMapper.readTree(response);
            if (node.has("data")) {
                return java.util.Base64.getDecoder().decode(node.get("data").asText());
            }
            return new byte[0];
        } catch (Exception e) {
            log.error("从Worker下载文件失败: {}", e.getMessage());
            throw new RuntimeException("从Worker下载文件失败", e);
        }
    }

    /**
     * 删除 Worker 上的文件
     */
    public void deleteFile(String workerUrl, String remotePath) {
        try {
            HttpUtil.post(workerUrl + "/worker/storage/delete", objectMapper.writeValueAsString(Map.of("path", remotePath)));
        } catch (Exception e) {
            log.warn("删除Worker文件失败: {}", e.getMessage());
        }
    }

    /**
     * 检查 Worker 上文件是否存在
     */
    public boolean fileExists(String workerUrl, String remotePath) {
        try {
            String url = workerUrl + "/worker/storage/exists?path=" + java.net.URLEncoder.encode(remotePath, "UTF-8");
            String response = HttpUtil.get(url);
            JsonNode node = objectMapper.readTree(response);
            return node.has("exists") && node.get("exists").asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在 Worker 上同步执行 shell 命令
     *
     * @param workerUrl Worker 地址
     * @param command   要执行的命令
     * @param timeout   超时秒数
     * @param workdir   工作目录（可为 null）
     * @return 包含 exit_code, success, output 的 Map
     */
    public Map<String, Object> executeShell(String workerUrl, String command, int timeout, String workdir) {
        try {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("command", command);
            body.put("timeout", timeout);
            if (workdir != null) body.put("workdir", workdir);

            String url = workerUrl + "/worker/shell/execute";
            String response = HttpUtil.post(url, objectMapper.writeValueAsString(body), authHeaders());
            JsonNode node = objectMapper.readTree(response);

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("exit_code", node.has("exit_code") ? node.get("exit_code").asInt() : -1);
            result.put("success", node.has("success") && node.get("success").asBoolean());
            result.put("output", node.has("output") ? node.get("output").asText() : "");
            return result;
        } catch (Exception e) {
            log.error("Worker shell 执行失败: workerUrl={}, error={}", workerUrl, e.getMessage());
            return Map.of("exit_code", -1, "success", false,
                    "output", "Worker 调用失败: " + e.getMessage());
        }
    }
}
