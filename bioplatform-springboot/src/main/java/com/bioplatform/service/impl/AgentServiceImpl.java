package com.bioplatform.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bioplatform.entity.AgentConversation;
import com.bioplatform.entity.AgentMessage;
import com.bioplatform.entity.AgentTool;
import com.bioplatform.entity.SystemConfig;
import com.bioplatform.mapper.AgentConversationMapper;
import com.bioplatform.mapper.AgentMessageMapper;
import com.bioplatform.mapper.AgentToolMapper;
import com.bioplatform.agent.ChatMessage;
import com.bioplatform.agent.LLMClient;
import com.bioplatform.agent.LLMResponse;
import com.bioplatform.agent.ToolCall;
import com.bioplatform.agent.ToolDefinition;
import com.bioplatform.agent.tools.AgentToolExecutor;
import com.bioplatform.service.SystemService;
import com.bioplatform.service.AgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI Agent服务实现类
 * 集成OpenAI兼容API的LLM调用
 *
 * @author luosg
 */
@Service
public class AgentServiceImpl implements AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentToolMapper toolMapper;
    private final SystemService systemService;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;
    private final LLMClient llmClient;
    private final AgentToolExecutor toolExecutor;
    private final javax.sql.DataSource dataSource;

    public AgentServiceImpl(AgentConversationMapper conversationMapper,
                            AgentMessageMapper messageMapper,
                            AgentToolMapper toolMapper,
                            SystemService systemService,
                            ObjectMapper objectMapper,
                            LLMClient llmClient,
                            AgentToolExecutor toolExecutor,
                            javax.sql.DataSource dataSource) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.toolMapper = toolMapper;
        this.systemService = systemService;
        this.objectMapper = objectMapper;
        this.llmClient = llmClient;
        this.toolExecutor = toolExecutor;
        this.dataSource = dataSource;

        // 配置OkHttp客户端（连接池复用，降低延迟）
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public AgentConversation createConversation(Long userId, Long projectId, String title, String modelName) {
        AgentConversation conversation = new AgentConversation();
        conversation.setUserId(userId);
        conversation.setProjectId(projectId);
        conversation.setTitle(title);
        conversation.setModelName(modelName);

        conversationMapper.insert(conversation);
        log.info("创建对话成功: conversationId={}, title={}", conversation.getId(), title);
        return conversation;
    }

    @Override
    public List<AgentConversation> listConversations(Long userId) {
        return conversationMapper.selectByUserId(userId);
    }

    @Override
    public AgentConversation getConversationById(Long id) {
        return conversationMapper.selectById(id);
    }

    @Override
    @Transactional
    public void deleteConversation(Long id) {
        AgentConversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在");
        }

        // 删除对话的所有消息
        List<AgentMessage> messages = messageMapper.selectByConversationId(id, null);
        for (AgentMessage message : messages) {
            messageMapper.deleteById(message.getId());
        }

        // 删除对话
        conversationMapper.deleteById(id);
        log.info("删除对话成功: conversationId={}", id);
    }

    @Override
    public List<AgentMessage> getMessages(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId, null);
    }

    @Override
    @Transactional
    public AgentMessage sendMessage(Long conversationId, String content, Long userId) {
        // 验证对话是否存在
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在");
        }

        // 保存用户消息
        AgentMessage userMessage = new AgentMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        messageMapper.insert(userMessage);

        // 获取历史消息（最近20条作为上下文）
        List<AgentMessage> historyMessages = messageMapper.selectRecentByConversationId(conversationId, 20);

        // 调用LLM API获取回复
        String assistantContent = callLlmApi(conversation.getModelName(), historyMessages);

        // 保存助手回复
        AgentMessage assistantMessage = new AgentMessage();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(assistantContent);
        messageMapper.insert(assistantMessage);

        // 更新对话的更新时间
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        log.info("发送消息成功: conversationId={}, userId={}", conversationId, userId);
        return assistantMessage;
    }

    @Override
    public SseEmitter streamChat(Long conversationId, String content, Long userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5分钟超时

        // SSE 心跳保活：每15秒发送一个注释行，防止 nginx/HTTP2 因空闲关闭连接
        java.util.concurrent.ScheduledExecutorService heartbeat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat-" + conversationId);
            t.setDaemon(true);
            return t;
        });
        java.util.concurrent.ScheduledFuture<?> heartbeatTask = heartbeat.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                heartbeat.shutdown();
            }
        }, 15, 15, java.util.concurrent.TimeUnit.SECONDS);

        // 连接结束时停止心跳
        emitter.onCompletion(() -> { heartbeatTask.cancel(false); heartbeat.shutdown(); });
        emitter.onTimeout(() -> { heartbeatTask.cancel(false); heartbeat.shutdown(); });
        emitter.onError(e -> { heartbeatTask.cancel(false); heartbeat.shutdown(); });

        // 保存用户消息
        AgentMessage userMessage = new AgentMessage();
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(content);
        messageMapper.insert(userMessage);

        // 获取历史消息（包含刚保存的用户消息）
        List<AgentMessage> historyMessages = messageMapper.selectRecentByConversationId(conversationId, 20);

        // 捕获当前线程的 SecurityContext，传播到异步线程
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // 异步执行：工具调用循环 + 流式输出
        new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                AgentConversation conversation = conversationMapper.selectById(conversationId);
                String assistantContent = processWithTools(conversation.getModelName(), historyMessages, emitter);

                // 流结束，保存完整助手回复
                if (assistantContent != null && !assistantContent.isEmpty()) {
                    AgentMessage assistantMessage = new AgentMessage();
                    assistantMessage.setConversationId(conversationId);
                    assistantMessage.setRole("assistant");
                    assistantMessage.setContent(assistantContent);
                    messageMapper.insert(assistantMessage);

                    // 更新对话时间
                    conversation.setUpdatedAt(LocalDateTime.now());
                    conversationMapper.updateById(conversation);

                    // 生成对话标题
                    if ("新对话".equals(conversation.getTitle())) {
                        generateTitle(conversation, content);
                    }
                }

                // 发送完成事件（连接可能已断开，忽略发送失败）
                try {
                    emitter.send(SseEmitter.event()
                            .data("{\"done\":true,\"conversationId\":" + conversationId + "}"));
                    emitter.complete();
                } catch (Exception ex) {
                    log.debug("SSE完成事件发送失败（客户端可能已断开）: {}", ex.getMessage());
                    try { emitter.complete(); } catch (Exception ignored) {}
                }

                log.info("流式消息发送成功: conversationId={}, userId={}", conversationId, userId);
            } catch (Exception e) {
                log.error("流式消息发送失败: conversationId={}, userId={}, error={}", conversationId, userId, e.getMessage(), e);
                try {
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isBlank()) errorMsg = "未知错误";
                    errorMsg = errorMsg.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                    emitter.send(SseEmitter.event()
                            .data("{\"error\":\"" + errorMsg + "\",\"conversationId\":" + conversationId + "}"));
                    emitter.complete();
                } catch (Exception ex) {
                    log.warn("SSE emitter 关闭失败: {}", ex.getMessage());
                    emitter.complete();
                }
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, "sse-stream-" + conversationId).start();

        return emitter;
    }

    /**
     * 工具调用循环 + 实时流式输出
     * 每个步骤（工具调用、工具结果、最终回复）都即时推送到前端
     */
    private String processWithTools(String modelName, List<AgentMessage> historyMessages,
                                     SseEmitter emitter) throws Exception {
        // 连接状态标记：SSE 断开时置 true，中断工具调用循环
        java.util.concurrent.atomic.AtomicBoolean disconnected = new java.util.concurrent.atomic.AtomicBoolean(false);
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onError(e -> disconnected.set(true));

        // 构建 ChatMessage 列表
        List<ChatMessage> messages = new java.util.ArrayList<>();
        for (AgentMessage msg : historyMessages) {
            if ("user".equals(msg.getRole())) {
                messages.add(ChatMessage.user(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(ChatMessage.assistant(msg.getContent()));
            }
        }

        // 系统提示词（含数据库schema，避免LLM浪费轮次探索表结构）
        String systemPrompt = "你是一个专业的生物信息学助手，擅长解答基因组学、转录组学、蛋白质组学等生物信息学相关问题。" +
                "你拥有以下工具能力：\n" +
                "1. database_query: 直接查询平台MySQL数据库(bioplatform)，执行SELECT查询。" +
                "当用户询问平台数据时使用此工具。数据库表结构如下：\n" + getDatabaseSchema() + "\n" +
                "2. shell_execute: 在服务器上执行shell命令。适用于查看文件、运行生信工具等。\n" +
                "重要规则：\n" +
                "- 查询数据库时直接写SQL，不要先 SHOW TABLES 或 DESCRIBE（表结构已提供）\n" +
                "- 用一条SQL获取数据（如 SELECT COUNT(*) FROM projects），不要分多步\n" +
                "- 尽量1轮工具调用完成，最多不超过2轮\n";

        // 获取工具定义
        List<ToolDefinition> tools = toolExecutor.getAllToolDefinitions();
        log.info("工具调用模式: {} 个工具可用", tools.size());

        sendSse(emitter, "status", "正在分析问题...");

        // 工具调用循环（最多5轮，防止死循环）
        LLMResponse response = llmClient.chatWithTools(messages, systemPrompt, tools);
        int toolRounds = 0;
        while (response.hasToolCalls() && toolRounds < 5) {
            // 客户端已断开，停止工具调用循环
            if (disconnected.get()) {
                log.info("SSE连接已断开，停止工具调用循环");
                return null;
            }

            toolRounds++;
            log.info("工具调用第{}轮: {}个工具", toolRounds, response.getToolCalls().size());

            // 将助手消息加入上下文（包含 tool_calls 信息，LLM API 要求）
            List<ChatMessage.ToolCallReference> toolCallRefs = response.getToolCalls().stream()
                    .map(tc -> new ChatMessage.ToolCallReference(tc.id(), tc.name(), tc.arguments()))
                    .toList();
            messages.add(ChatMessage.assistantWithToolCalls(response.getContent(), toolCallRefs));

            // 执行每个工具调用
            for (ToolCall toolCall : response.getToolCalls()) {
                // 每次工具调用前检查连接状态
                if (disconnected.get()) {
                    log.info("SSE连接已断开，停止工具调用");
                    return null;
                }

                log.info("执行工具: {}", toolCall.name());

                // 推送工具调用事件
                String argsDisplay = toolCall.arguments();
                try {
                    Map<String, Object> parsed = objectMapper.readValue(toolCall.arguments(), new TypeReference<>() {});
                    argsDisplay = objectMapper.writeValueAsString(parsed);
                } catch (Exception ignored) {}
                sendSse(emitter, "tool_call",
                        "{\"name\":\"" + escapeJson(toolCall.name()) +
                        "\",\"arguments\":" + argsDisplay + "}");

                // 执行工具
                String result;
                try {
                    Map<String, String> args = objectMapper.readValue(
                            toolCall.arguments(), new TypeReference<>() {});
                    result = toolExecutor.executeTool(toolCall.name(), args);
                } catch (Exception e) {
                    result = "{\"error\": \"工具执行失败: " + e.getMessage() + "\"}";
                }

                // 推送工具结果事件（截断过长的输出）
                String resultPreview = result.length() > 500 ? result.substring(0, 500) + "..." : result;
                sendSse(emitter, "tool_result",
                        "{\"name\":\"" + escapeJson(toolCall.name()) +
                        "\",\"output\":" + objectMapper.writeValueAsString(resultPreview) + "}");

                messages.add(ChatMessage.tool(toolCall.id(), result));
            }

            sendSse(emitter, "status", "正在根据工具结果生成回答...");
            response = llmClient.chatWithTools(messages, systemPrompt, tools);
        }

        // 如果循环结束后 response 仍含 tool_calls（3轮用完），再做一次无工具调用获取文本回复
        if (response.hasToolCalls()) {
            log.info("工具调用轮次已用完，执行无工具 LLM 调用生成文本回复");
            // 把最后的助手 tool_calls 消息加入上下文
            List<ChatMessage.ToolCallReference> lastRefs = response.getToolCalls().stream()
                    .map(tc -> new ChatMessage.ToolCallReference(tc.id(), tc.name(), tc.arguments()))
                    .toList();
            messages.add(ChatMessage.assistantWithToolCalls(response.getContent(), lastRefs));
            // 无工具调用
            response = llmClient.chatWithTools(messages, systemPrompt, null);
        }

        // 获取最终文本回复
        String finalContent = response.getContent();
        if (finalContent == null || finalContent.isEmpty()) {
            finalContent = "抱歉，我无法处理您的请求。";
        }

        // 流式推送到前端（每20字符一个chunk，模拟流式效果）
        for (int i = 0; i < finalContent.length(); i += 20) {
            if (disconnected.get()) break;
            int end = Math.min(i + 20, finalContent.length());
            String chunk = finalContent.substring(i, end);
            emitter.send(SseEmitter.event()
                    .data("{\"delta\":\"" + escapeJson(chunk) + "\"}"));
        }

        return finalContent;
    }

    /**
     * 获取数据库schema信息，注入到系统提示词中
     * 避免LLM浪费工具调用轮次去探索表结构
     */
    private String getDatabaseSchema() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            java.sql.ResultSet rs = stmt.executeQuery(
                    "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE " +
                    "FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "ORDER BY TABLE_NAME, ORDINAL_POSITION");
            StringBuilder sb = new StringBuilder();
            String lastTable = "";
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                String column = rs.getString("COLUMN_NAME");
                String type = rs.getString("DATA_TYPE");
                if (!table.equals(lastTable)) {
                    if (!lastTable.isEmpty()) sb.append(")\n");
                    sb.append(table).append("(").append(column).append(" ").append(type);
                    lastTable = table;
                } else {
                    sb.append(", ").append(column).append(" ").append(type);
                }
            }
            if (sb.length() > 0) sb.append(")");
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取数据库schema失败: {}", e.getMessage());
            return "(schema获取失败，请先用 SHOW TABLES 探索)";
        }
    }

    /**
     * 发送 SSE 事件
     */
    private void sendSse(SseEmitter emitter, String eventType, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .data("{\"" + eventType + "\":" + data + "}"));
        } catch (Exception e) {
            log.warn("SSE 发送失败: {}", e.getMessage());
        }
    }

    /**
     * 流式调用LLM API，逐token推送到SseEmitter（保留作为无工具的备用方案）
     */
    private String streamLlmApi(String modelName, List<AgentMessage> historyMessages, SseEmitter emitter) throws Exception {
        String apiKey = systemService.getConfigValue("llm_api_key");
        String model = systemService.getConfigValue("llm_model");
        String baseUrl = systemService.getConfigValue("llm_base_url");

        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("LLM API Key 未配置，请在后台系统配置中设置");
        }
        if (apiKey.contains("***")) {
            throw new RuntimeException("LLM API Key 为遮蔽值，请在后台重新输入真实的 API Key");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new RuntimeException("LLM Base URL 未配置，请在后台系统配置中设置");
        }
        if (model == null || model.isBlank()) {
            throw new RuntimeException("LLM 模型名称未配置，请在后台系统配置中设置");
        }
        if (modelName != null && !modelName.isBlank()) {
            model = modelName;
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 2000);
        requestBody.put("stream", true);

        ArrayNode messagesArray = requestBody.putArray("messages");
        ObjectNode systemMessage = messagesArray.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是一个专业的生物信息学助手，擅长解答基因组学、转录组学、蛋白质组学等生物信息学相关问题。请用专业但易懂的语言回答用户的问题。");
        for (AgentMessage msg : historyMessages) {
            ObjectNode messageNode = messagesArray.addObject();
            messageNode.put("role", msg.getRole());
            messageNode.put("content", msg.getContent());
        }

        String url = baseUrl + "/chat/completions";
        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json"));
        Request request;
        try {
            request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("LLM API Key 格式不正确，请在后台系统配置中重新设置: " + e.getMessage());
        }

        Response response;
        try {
            response = httpClient.newCall(request).execute();
        } catch (Exception e) {
            throw new RuntimeException("LLM API 连接失败，请检查网络和配置: " + e.getMessage());
        }
        if (!response.isSuccessful()) {
            response.close();
            throw new RuntimeException("LLM API调用失败: " + response.code());
        }

        StringBuilder fullContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode json = objectMapper.readTree(data);
                        JsonNode choices = json.get("choices");
                        if (choices != null && choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                String token = delta.get("content").asText();
                                fullContent.append(token);
                                emitter.send(SseEmitter.event()
                                        .data("{\"delta\":\"" + escapeJson(token) + "\"}"));
                            }
                        }
                    } catch (Exception ignored) {
                        // 跳过无法解析的行
                    }
                }
            }
        }
        return fullContent.toString();
    }

    /**
     * 生成对话标题（用用户首条消息截断，不发LLM请求）
     */
    private void generateTitle(AgentConversation conversation, String userMessage) {
        try {
            String title = userMessage.replaceAll("\\s+", " ").trim();
            if (title.length() > 20) title = title.substring(0, 20);
            conversation.setTitle(title);
            conversationMapper.updateById(conversation);
            log.info("对话标题已更新: id={}, title={}", conversation.getId(), title);
        } catch (Exception e) {
            log.warn("生成对话标题失败: {}", e.getMessage());
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public List<AgentTool> listEnabledTools() {
        return toolMapper.selectEnabled();
    }

    /**
     * 调用LLM API
     *
     * @param modelName      模型名称
     * @param historyMessages 历史消息
     * @return 助手回复内容
     */
    private String callLlmApi(String modelName, List<AgentMessage> historyMessages) {
        try {
            // 从数据库读取 LLM 配置
            String apiKey = systemService.getConfigValue("llm_api_key");
            String model = systemService.getConfigValue("llm_model");
            String baseUrl = systemService.getConfigValue("llm_base_url");

            if (apiKey == null || apiKey.isBlank()) {
                throw new RuntimeException("LLM API Key 未配置，请在后台系统配置中设置");
            }
            if (apiKey.contains("***")) {
                throw new RuntimeException("LLM API Key 为遮蔽值，请在后台重新输入真实的 API Key");
            }
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new RuntimeException("LLM Base URL 未配置，请在后台系统配置中设置");
            }
            if (model == null || model.isBlank()) {
                throw new RuntimeException("LLM 模型名称未配置，请在后台系统配置中设置");
            }

            // 如果对话指定了模型名称，覆盖配置
            if (modelName != null && !modelName.isBlank()) {
                model = modelName;
            }

            // 构建请求体
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 2000);

            // 构建消息列表
            ArrayNode messagesArray = requestBody.putArray("messages");

            // 添加系统提示
            ObjectNode systemMessage = messagesArray.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个专业的生物信息学助手，擅长解答基因组学、转录组学、蛋白质组学等生物信息学相关问题。请用专业但易懂的语言回答用户的问题。");

            // 添加历史消息
            for (AgentMessage msg : historyMessages) {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
            }

            // 构建HTTP请求
            String url = baseUrl + "/chat/completions";
            RequestBody body = RequestBody.create(
                    objectMapper.writeValueAsString(requestBody),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // 发送请求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("LLM API调用失败: {}", response.code());
                    throw new RuntimeException("LLM API调用失败: " + response.code());
                }

                String responseBody = response.body().string();
                JsonNode responseJson = objectMapper.readTree(responseBody);

                // 解析响应
                JsonNode choices = responseJson.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode firstChoice = choices.get(0);
                    JsonNode message = firstChoice.get("message");
                    if (message != null) {
                        return message.get("content").asText();
                    }
                }

                throw new RuntimeException("LLM API响应格式错误");
            }
        } catch (IOException e) {
            log.error("调用LLM API异常", e);
            throw new RuntimeException("调用LLM API失败: " + e.getMessage(), e);
        }
    }
}
