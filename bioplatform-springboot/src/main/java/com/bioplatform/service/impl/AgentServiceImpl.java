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
import com.bioplatform.mapper.SystemConfigMapper;
import com.bioplatform.service.AgentService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
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
    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public AgentServiceImpl(AgentConversationMapper conversationMapper,
                            AgentMessageMapper messageMapper,
                            AgentToolMapper toolMapper,
                            SystemConfigMapper systemConfigMapper,
                            ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.toolMapper = toolMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;

        // 配置OkHttp客户端
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
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
            // 从系统配置中获取LLM配置
            SystemConfig apiKeyConfig = systemConfigMapper.selectByKey("llm_api_key");
            SystemConfig modelConfig = systemConfigMapper.selectByKey("llm_model");
            SystemConfig baseUrlConfig = systemConfigMapper.selectByKey("llm_base_url");

            String apiKey = apiKeyConfig != null ? apiKeyConfig.getConfigValue() : "";
            String model = modelConfig != null ? modelConfig.getConfigValue() : "gpt-3.5-turbo";
            String baseUrl = baseUrlConfig != null ? baseUrlConfig.getConfigValue() : "https://api.openai.com/v1";

            // 如果指定了模型名称，使用指定的模型
            if (modelName != null && !modelName.isEmpty()) {
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
