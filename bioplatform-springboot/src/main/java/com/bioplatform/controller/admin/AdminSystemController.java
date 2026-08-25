package com.bioplatform.controller.admin;

import com.bioplatform.common.util.AesEncryptUtil;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.service.SystemService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Admin system configuration and dashboard controller.
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final SystemService systemService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AdminSystemController(SystemService systemService, ObjectMapper objectMapper) {
        this.systemService = systemService;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    @GetMapping("/configs")
    public ApiResponse<List<com.bioplatform.entity.SystemConfig>> listConfigs() {
        return ApiResponse.success(systemService.getAllConfigs());
    }

    @PutMapping("/configs")
    @com.bioplatform.common.annotation.OperLog(module = "系统管理", operation = "更新系统配置")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, String> params) {
        String key = params.get("key");
        String value = params.get("value");
        if (key == null || key.isBlank()) {
            return ApiResponse.error(400, "配置键不能为空");
        }
        systemService.updateConfig(key, value);
        return ApiResponse.success();
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.success(systemService.getDashboardStats());
    }

    /**
     * 调用提供商 /v1/models 接口获取可用模型列表
     * 请求参数: baseUrl, apiKey（明文，仅此次调用使用，不存储）
     */
    @PostMapping("/llm/fetch-models")
    public ApiResponse<List<String>> fetchModels(@RequestBody Map<String, String> params) {
        String baseUrl = params.get("baseUrl");
        String apiKey = params.get("apiKey");

        if (baseUrl == null || baseUrl.isBlank()) {
            return ApiResponse.error(400, "Base URL 不能为空");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return ApiResponse.error(400, "API Key 不能为空");
        }

        // 如果传入的是遮蔽值或 ENC: 加密值，从数据库读取真实 key
        if (apiKey.contains("***") || AesEncryptUtil.isEncrypted(apiKey)) {
            apiKey = systemService.getConfigValue("llm_api_key");
            if (apiKey == null || apiKey.isBlank()) {
                return ApiResponse.error(400, "数据库中未配置 API Key，请先填写并保存");
            }
        }

        String url = baseUrl.replaceAll("/+$", "") + "/models";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                return ApiResponse.error(response.code(), "获取模型列表失败: " + response.code() + " " + body);
            }

            String body = response.body().string();
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");

            List<String> models = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode model : data) {
                    String id = model.has("id") ? model.get("id").asText() : null;
                    if (id != null && !id.isEmpty()) {
                        models.add(id);
                    }
                }
            }
            if (models.isEmpty()) {
                return ApiResponse.error(500, "返回的模型列表为空，响应: " + body.substring(0, Math.min(body.length(), 200)));
            }
            models.sort(String::compareToIgnoreCase);
            return ApiResponse.success(models);
        } catch (Exception e) {
            return ApiResponse.error(500, "连接失败: " + e.getMessage());
        }
    }
}
