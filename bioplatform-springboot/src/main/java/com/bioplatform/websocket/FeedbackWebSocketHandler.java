package com.bioplatform.websocket;

import com.bioplatform.common.util.JwtTokenProviderUtil;
import com.bioplatform.entity.FeedbackMessage;
import com.bioplatform.entity.FeedbackSession;
import com.bioplatform.service.FeedbackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反馈客服WebSocket处理器
 * 支持用户和管理员实时通信
 *
 * @author luosg
 */
@Component
public class FeedbackWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FeedbackWebSocketHandler.class);

    private final FeedbackService feedbackService;
    private final JwtTokenProviderUtil jwtTokenProviderUtil;
    private final ObjectMapper objectMapper;

    /** 用户会话: userId -> WebSocketSession */
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    /** 管理员会话集合 */
    private final Set<WebSocketSession> adminSessions = ConcurrentHashMap.newKeySet();
    /** WebSocketSession -> userId (用于关闭时清理) */
    private final Map<WebSocketSession, Long> sessionUserMap = new ConcurrentHashMap<>();
    /** WebSocketSession -> role */
    private final Map<WebSocketSession, String> sessionRoleMap = new ConcurrentHashMap<>();

    public FeedbackWebSocketHandler(FeedbackService feedbackService,
                                     JwtTokenProviderUtil jwtTokenProviderUtil,
                                     ObjectMapper objectMapper) {
        this.feedbackService = feedbackService;
        this.jwtTokenProviderUtil = jwtTokenProviderUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        String token = null;
        if (query != null) {
            Map<String, String> params = UriComponentsBuilder
                    .fromUriString("?" + query).build()
                    .getQueryParams().toSingleValueMap();
            token = params.get("token");
        }

        if (token == null || !jwtTokenProviderUtil.validateToken(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
            return;
        }

        Long userId = jwtTokenProviderUtil.getUserIdFromToken(token);
        String username = jwtTokenProviderUtil.getUsernameFromToken(token);

        // 判断是否管理员（通过查询角色）
        // 简化处理：管理员通过URL参数 role=admin 标识，或通过查DB
        // 这里用 query param 简化
        String role = "user";
        if (query != null) {
            Map<String, String> params = UriComponentsBuilder
                    .fromUriString("?" + query).build()
                    .getQueryParams().toSingleValueMap();
            if ("admin".equals(params.get("role"))) {
                role = "admin";
            }
        }

        sessionRoleMap.put(session, role);
        sessionUserMap.put(session, userId);

        if ("admin".equals(role)) {
            adminSessions.add(session);
            log.info("管理员WebSocket连接: userId={}, username={}", userId, username);
        } else {
            userSessions.put(userId, session);
            log.info("用户WebSocket连接: userId={}, username={}", userId, username);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            if (!"message".equals(type)) {
                // 心跳响应
                if ("ping".equals(type)) {
                    session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                }
                return;
            }

            String role = sessionRoleMap.get(session);
            Long userId = sessionUserMap.get(session);
            String content = (String) data.get("content");
            if (content == null || content.isBlank()) return;

            if ("user".equals(role)) {
                handleUserMessage(session, userId, content, data);
            } else if ("admin".equals(role)) {
                handleAdminMessage(session, userId, content, data);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息异常: {}", e.getMessage(), e);
        }
    }

    private void handleUserMessage(WebSocketSession session, Long userId, String content,
                                    Map<String, Object> data) throws IOException {
        // 获取或创建会话
        String userName = (String) data.get("userName");
        FeedbackSession fbSession = feedbackService.getOrCreateSession(userId, userName);
        Long sessionId = fbSession.getId();

        // 保存消息
        FeedbackMessage msg = feedbackService.addMessage(sessionId, "user", userName != null ? userName : "用户", content);

        // 构造消息JSON
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "message");
        msgData.put("sessionId", sessionId);
        msgData.put("senderType", "user");
        msgData.put("senderName", userName != null ? userName : "用户");
        msgData.put("content", content);
        msgData.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
        String msgJson = objectMapper.writeValueAsString(msgData);

        // 如果是第一条消息，通知所有管理员有新会话
        if (fbSession.getStatus() != null && fbSession.getStatus() == 0) {
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("id", sessionId);
            sessionData.put("userId", userId);
            sessionData.put("userName", userName != null ? userName : "匿名用户");
            sessionData.put("status", 0);

            Map<String, Object> newSessionPayload = new HashMap<>();
            newSessionPayload.put("type", "new_session");
            newSessionPayload.put("session", sessionData);
            String newSessionJson = objectMapper.writeValueAsString(newSessionPayload);
            for (WebSocketSession adminSession : adminSessions) {
                if (adminSession.isOpen()) {
                    adminSession.sendMessage(new TextMessage(newSessionJson));
                }
            }
        }

        // 转发消息给管理员
        for (WebSocketSession adminSession : adminSessions) {
            if (adminSession.isOpen()) {
                adminSession.sendMessage(new TextMessage(msgJson));
            }
        }

        // 发送确认给用户
        Map<String, Object> sentData = new HashMap<>();
        sentData.put("type", "sent");
        sentData.put("sessionId", sessionId);
        sentData.put("messageId", msg.getId());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sentData)));
    }

    private void handleAdminMessage(WebSocketSession session, Long adminUserId, String content,
                                     Map<String, Object> data) throws IOException {
        Object sidObj = data.get("sessionId");
        if (sidObj == null) return;
        Long sessionId = Long.valueOf(sidObj.toString());

        FeedbackSession fbSession = feedbackService.getSessionById(sessionId);
        if (fbSession == null) return;

        // 保存消息
        FeedbackMessage msg = feedbackService.addMessage(sessionId, "admin", "客服", content);

        // 构造消息JSON
        Map<String, Object> adminMsgData = new HashMap<>();
        adminMsgData.put("type", "message");
        adminMsgData.put("sessionId", sessionId);
        adminMsgData.put("senderType", "admin");
        adminMsgData.put("senderName", "客服");
        adminMsgData.put("content", content);
        adminMsgData.put("createdAt", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
        String msgJson = objectMapper.writeValueAsString(adminMsgData);

        // 发送给对应的用户
        Long targetUserId = fbSession.getUserId();
        if (targetUserId != null) {
            WebSocketSession userSession = userSessions.get(targetUserId);
            if (userSession != null && userSession.isOpen()) {
                userSession.sendMessage(new TextMessage(msgJson));
            }
        }

        // 回显给管理员
        session.sendMessage(new TextMessage(msgJson));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String role = sessionRoleMap.remove(session);
        Long userId = sessionUserMap.remove(session);

        if ("admin".equals(role)) {
            adminSessions.remove(session);
            log.info("管理员WebSocket断开: userId={}", userId);
        } else if (userId != null) {
            userSessions.remove(userId);
            log.info("用户WebSocket断开: userId={}", userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket传输错误: {}", exception.getMessage());
    }
}
