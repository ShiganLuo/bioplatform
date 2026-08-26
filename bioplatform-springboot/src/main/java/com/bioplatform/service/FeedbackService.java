package com.bioplatform.service;

import com.bioplatform.entity.FeedbackMessage;
import com.bioplatform.entity.FeedbackSession;

import java.util.List;

/**
 * 反馈服务接口
 *
 * @author luosg
 */
public interface FeedbackService {

    /**
     * 获取或创建用户的open会话
     */
    FeedbackSession getOrCreateSession(Long userId, String userName);

    /**
     * 创建新会话
     */
    FeedbackSession createSession(Long userId, String userName);

    /**
     * 添加消息
     */
    FeedbackMessage addMessage(Long sessionId, String senderType, String senderName, String content);

    /**
     * 获取会话消息列表
     */
    List<FeedbackMessage> getMessages(Long sessionId);

    /**
     * 获取所有open会话
     */
    List<FeedbackSession> listOpenSessions();

    /**
     * 关闭会话
     */
    void closeSession(Long sessionId);

    /**
     * 根据ID获取会话
     */
    FeedbackSession getSessionById(Long sessionId);
}
