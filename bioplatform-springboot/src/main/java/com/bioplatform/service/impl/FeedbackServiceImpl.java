package com.bioplatform.service.impl;

import com.bioplatform.entity.FeedbackMessage;
import com.bioplatform.entity.FeedbackSession;
import com.bioplatform.mapper.FeedbackMessageMapper;
import com.bioplatform.mapper.FeedbackSessionMapper;
import com.bioplatform.service.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 反馈服务实现类
 *
 * @author luosg
 */
@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackServiceImpl.class);

    private final FeedbackSessionMapper sessionMapper;
    private final FeedbackMessageMapper messageMapper;

    public FeedbackServiceImpl(FeedbackSessionMapper sessionMapper,
                               FeedbackMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public FeedbackSession getOrCreateSession(Long userId, String userName) {
        FeedbackSession session = sessionMapper.selectOpenByUserId(userId);
        if (session == null) {
            session = createSession(userId, userName);
        }
        return session;
    }

    @Override
    @Transactional
    public FeedbackSession createSession(Long userId, String userName) {
        FeedbackSession session = new FeedbackSession();
        session.setUserId(userId);
        session.setUserName(userName != null ? userName : "匿名用户");
        session.setStatus(0);
        sessionMapper.insert(session);
        log.info("创建反馈会话: sessionId={}, userId={}", session.getId(), userId);
        return session;
    }

    @Override
    @Transactional
    public FeedbackMessage addMessage(Long sessionId, String senderType, String senderName, String content) {
        FeedbackMessage message = new FeedbackMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setSenderName(senderName);
        message.setContent(content);
        messageMapper.insert(message);
        return message;
    }

    @Override
    public List<FeedbackMessage> getMessages(Long sessionId) {
        return messageMapper.selectBySessionId(sessionId);
    }

    @Override
    public List<FeedbackSession> listOpenSessions() {
        return sessionMapper.selectOpenSessions();
    }

    @Override
    @Transactional
    public void closeSession(Long sessionId) {
        sessionMapper.updateStatus(sessionId, 1);
        log.info("关闭反馈会话: sessionId={}", sessionId);
    }

    @Override
    public FeedbackSession getSessionById(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }
}
