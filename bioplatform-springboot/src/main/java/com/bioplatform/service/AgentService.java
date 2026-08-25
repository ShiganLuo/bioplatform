package com.bioplatform.service;

import com.bioplatform.entity.AgentConversation;
import com.bioplatform.entity.AgentMessage;
import com.bioplatform.entity.AgentTool;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI Agent服务接口
 *
 * @author luosg
 */
public interface AgentService {

    /**
     * 创建对话
     *
     * @param userId    用户ID
     * @param projectId 项目ID
     * @param title     对话标题
     * @param modelName 模型名称
     * @return 对话信息
     */
    AgentConversation createConversation(Long userId, Long projectId, String title, String modelName);

    /**
     * 查询用户的对话列表
     *
     * @param userId 用户ID
     * @return 对话列表
     */
    List<AgentConversation> listConversations(Long userId);

    /**
     * 根据ID获取对话
     *
     * @param id 对话ID
     * @return 对话信息
     */
    AgentConversation getConversationById(Long id);

    /**
     * 删除对话
     *
     * @param id 对话ID
     */
    void deleteConversation(Long id);

    /**
     * 获取对话的消息列表
     *
     * @param conversationId 对话ID
     * @return 消息列表
     */
    List<AgentMessage> getMessages(Long conversationId);

    /**
     * 发送消息
     *
     * @param conversationId 对话ID
     * @param content        消息内容
     * @param userId         用户ID
     * @return 助手回复消息
     */
    AgentMessage sendMessage(Long conversationId, String content, Long userId);

    /**
     * 流式发送消息（SSE）
     *
     * @param conversationId 对话ID
     * @param content        消息内容
     * @param userId         用户ID
     * @return SSE发射器
     */
    SseEmitter streamChat(Long conversationId, String content, Long userId);

    /**
     * 查询启用的工具列表
     *
     * @return 工具列表
     */
    List<AgentTool> listEnabledTools();
}
