package com.bioplatform.agent;

import com.bioplatform.common.util.AesEncryptUtil;
import com.bioplatform.entity.SystemConfig;
import com.bioplatform.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM客户端 - 基于OkHttp的OpenAI兼容API调用客户端
 * <p>
 * 支持：
 * - 普通对话请求（chat）
 * - 工具调用请求（chatWithTools）
 * - SSE流式响应（chatStream）
 * - 自动重试逻辑
 * </p>
 *
 * @author luosg
 */
@Component
public class LLMClient {

    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;
    /** 重试间隔（毫秒） */
    private static final long RETRY_DELAY_MS = 1000;

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public LLMClient(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // 支持 HTTP_PROXY 环境变量
        String httpProxy = System.getenv("HTTP_PROXY");
        if (httpProxy == null || httpProxy.isEmpty()) {
            httpProxy = System.getenv("http_proxy");
        }
        if (httpProxy != null && !httpProxy.isEmpty()) {
            try {
                // 解析 http://host:port 格式
                String proxyUrl = httpProxy.replace("http://", "").replace("https://", "");
                String[] parts = proxyUrl.split(":");
                String proxyHost = parts[0];
                int proxyPort = parts.length > 1 ? Integer.parseInt(parts[1]) : 3128;
                log.info("使用HTTP代理: {}:{}", proxyHost, proxyPort);
                builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
            } catch (Exception e) {
                log.warn("解析HTTP_PROXY失败，不使用代理: {}", e.getMessage());
            }
        }

        this.httpClient = builder.build();
    }

    /**
     * 获取LLM API配置
     */
    private LLMConfig loadConfig() {
        SystemConfig apiKeyConfig = systemConfigMapper.selectByKey("llm_api_key");
        SystemConfig modelConfig = systemConfigMapper.selectByKey("llm_model");
        SystemConfig baseUrlConfig = systemConfigMapper.selectByKey("llm_base_url");

        String apiKey = apiKeyConfig != null ? AesEncryptUtil.decrypt(apiKeyConfig.getConfigValue()) : "";
        String model = modelConfig != null ? modelConfig.getConfigValue() : "gpt-3.5-turbo";
        String baseUrl = baseUrlConfig != null ? baseUrlConfig.getConfigValue() : "https://api.openai.com/v1";

        return new LLMConfig(apiKey, model, baseUrl);
    }

    /**
     * 普通对话请求
     *
     * @param messages     消息列表
     * @param systemPrompt 系统提示词
     * @return 助手回复文本
     */
    public String chat(List<ChatMessage> messages, String systemPrompt) {
        LLMResponse response = chatWithTools(messages, systemPrompt, null);
        return response.getContent();
    }

    /**
     * 带工具定义的对话请求
     *
     * @param messages     消息列表
     * @param systemPrompt 系统提示词
     * @param tools        可用工具列表（可为null）
     * @return LLM响应（包含文本和/或工具调用）
     */
    public LLMResponse chatWithTools(List<ChatMessage> messages, String systemPrompt,
                                     List<ToolDefinition> tools) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doChatWithTools(messages, systemPrompt, tools);
            } catch (IOException e) {
                log.error("LLM API调用失败 (第{}次): {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("LLM调用被中断", ie);
                    }
                } else {
                    throw new RuntimeException("LLM API调用失败，已重试" + MAX_RETRIES + "次: " + e.getMessage(), e);
                }
            }
        }
        throw new RuntimeException("LLM API调用异常");
    }

    /**
     * SSE流式对话请求
     *
     * @param messages       消息列表
     * @param systemPrompt   系统提示词
     * @param onToken        每收到一个token的回调
     * @return 完整的回复文本
     */
    public String chatStream(List<ChatMessage> messages, String systemPrompt, StreamCallback onToken) {
        LLMConfig config = loadConfig();

        try {
            ObjectNode requestBody = buildRequestBody(messages, systemPrompt, config.model, null);
            requestBody.put("stream", true);

            String url = config.baseUrl + "/chat/completions";
            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + config.apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    throw new IOException("LLM API调用失败: " + response.code() + " - " + errorBody);
                }

                StringBuilder fullContent = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (!line.startsWith("data: ")) {
                            continue;
                        }
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        JsonNode json = objectMapper.readTree(data);
                        JsonNode choices = json.get("choices");
                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content")) {
                                String token = delta.get("content").asText();
                                fullContent.append(token);
                                if (onToken != null) {
                                    onToken.onToken(token);
                                }
                            }
                        }
                    }
                }
                return fullContent.toString();
            }
        } catch (IOException e) {
            log.error("SSE流式调用失败", e);
            throw new RuntimeException("SSE流式调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行实际的chatWithTools请求
     */
    private LLMResponse doChatWithTools(List<ChatMessage> messages, String systemPrompt,
                                        List<ToolDefinition> tools) throws IOException {
        LLMConfig config = loadConfig();

        ObjectNode requestBody = buildRequestBody(messages, systemPrompt, config.model, tools);

        String url = config.baseUrl + "/chat/completions";
        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + config.apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("LLM API调用失败: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode responseJson = objectMapper.readTree(responseBody);

            return parseResponse(responseJson);
        }
    }

    /**
     * 构建请求体
     */
    private ObjectNode buildRequestBody(List<ChatMessage> messages, String systemPrompt,
                                         String model, List<ToolDefinition> tools) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 4096);

        ArrayNode messagesArray = requestBody.putArray("messages");

        // 添加系统消息
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode systemMsg = messagesArray.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
        }

        // 添加历史消息
        for (ChatMessage msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.role());
            if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }
            if (msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }
            // 序列化助手消息中的 tool_calls
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode tcArray = msgNode.putArray("tool_calls");
                for (ChatMessage.ToolCallReference tc : msg.toolCalls()) {
                    ObjectNode tcNode = tcArray.addObject();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode fnNode = tcNode.putObject("function");
                    fnNode.put("name", tc.name());
                    fnNode.put("arguments", tc.arguments());
                }
            }
        }

        // 添加工具定义
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolObj = toolsArray.addObject();
                toolObj.put("type", "function");

                ObjectNode functionObj = toolObj.putObject("function");
                functionObj.put("name", tool.name());
                functionObj.put("description", tool.description());
                if (tool.parameters() != null) {
                    functionObj.set("parameters", objectMapper.valueToTree(tool.parameters()));
                }
            }
        }

        return requestBody;
    }

    /**
     * 解析LLM响应
     */
    private LLMResponse parseResponse(JsonNode responseJson) {
        JsonNode choices = responseJson.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new RuntimeException("LLM API响应中没有choices");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode messageNode = firstChoice.get("message");
        if (messageNode == null) {
            throw new RuntimeException("LLM API响应中没有message");
        }

        String content = messageNode.has("content") && !messageNode.get("content").isNull()
                ? messageNode.get("content").asText() : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = messageNode.get("tool_calls");
        if (toolCallsNode != null && toolCallsNode.isArray()) {
            for (JsonNode tc : toolCallsNode) {
                String id = tc.has("id") ? tc.get("id").asText() : null;
                JsonNode function = tc.get("function");
                String name = function != null && function.has("name") ? function.get("name").asText() : null;
                String arguments = function != null && function.has("arguments") ? function.get("arguments").asText() : "{}";
                toolCalls.add(new ToolCall(id, name, arguments));
            }
        }

        return new LLMResponse(content, toolCalls);
    }

    /**
     * 流式回调接口
     */
    @FunctionalInterface
    public interface StreamCallback {
        void onToken(String token);
    }

    /**
     * LLM配置内部类
     */
    private static class LLMConfig {
        final String apiKey;
        final String model;
        final String baseUrl;

        LLMConfig(String apiKey, String model, String baseUrl) {
            this.apiKey = apiKey;
            this.model = model;
            this.baseUrl = baseUrl;
        }
    }
}
