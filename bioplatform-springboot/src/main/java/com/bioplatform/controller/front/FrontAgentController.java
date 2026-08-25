package com.bioplatform.controller.front;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.AgentConversation;
import com.bioplatform.entity.AgentMessage;
import com.bioplatform.entity.AgentTool;
import com.bioplatform.service.AgentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Front-end AI Agent controller (optional auth).
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front/agent")
public class FrontAgentController {

    private final AgentService agentService;

    public FrontAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Chat with AI (optional auth, uses anonymous user if not authenticated).
     */
    @PostMapping("/chat")
    public ApiResponse<AgentMessage> chat(@RequestBody Map<String, Object> params) {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(400, "请先登录后再使用 AI 助手");
        }

        Long conversationId = params.get("conversationId") != null
                ? Long.valueOf(params.get("conversationId").toString()) : null;
        // 前端可能发 "message" 或 "content"，兼容两种字段名
        Object contentObj = params.get("content");
        if (contentObj == null) {
            contentObj = params.get("message");
        }
        if (contentObj == null || contentObj.toString().isBlank()) {
            return ApiResponse.error(400, "消息内容不能为空");
        }
        String content = contentObj.toString().trim();

        // If no conversation ID, create a new conversation first
        if (conversationId == null) {
            com.bioplatform.entity.AgentConversation conversation =
                    agentService.createConversation(userId, null, "新对话", null);
            conversationId = conversation.getId();
        }

        AgentMessage response = agentService.sendMessage(conversationId, content, userId);
        return ApiResponse.success(response);
    }

    /**
     * 流式聊天（SSE）
     */
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody Map<String, Object> params) {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            SseEmitter errEmitter = new SseEmitter();
            try {
                errEmitter.send(SseEmitter.event().data("{\"error\":\"请先登录后再使用 AI 助手\"}"));
                errEmitter.complete();
            } catch (Exception ignored) {}
            return errEmitter;
        }

        Long conversationId = params.get("conversationId") != null
                ? Long.valueOf(params.get("conversationId").toString()) : null;
        Object contentObj = params.get("content");
        if (contentObj == null) {
            contentObj = params.get("message");
        }
        if (contentObj == null || contentObj.toString().isBlank()) {
            SseEmitter errEmitter = new SseEmitter();
            try {
                errEmitter.send(SseEmitter.event().data("{\"error\":\"消息内容不能为空\"}"));
                errEmitter.complete();
            } catch (Exception ignored) {}
            return errEmitter;
        }
        String content = contentObj.toString().trim();

        if (conversationId == null) {
            AgentConversation conversation =
                    agentService.createConversation(userId, null, "新对话", null);
            conversationId = conversation.getId();
        }

        return agentService.streamChat(conversationId, content, userId);
    }

    /**
     * List available agent tools.
     */
    @GetMapping("/tools")
    public ApiResponse<List<AgentTool>> listTools() {
        List<AgentTool> tools = agentService.listEnabledTools();
        return ApiResponse.success(tools);
    }

    /**
     * List current user's conversations.
     */
    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversation>> listConversations() {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(400, "请先登录");
        }
        return ApiResponse.success(agentService.listConversations(userId));
    }

    /**
     * Get messages of a conversation.
     */
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AgentMessage>> getMessages(@PathVariable Long id) {
        return ApiResponse.success(agentService.getMessages(id));
    }

    /**
     * Delete a conversation.
     */
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(400, "请先登录");
        }
        agentService.deleteConversation(id);
        return ApiResponse.success();
    }

    /**
     * 批量删除对话
     */
    @PostMapping("/conversations/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody Map<String, Object> params) {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(400, "请先登录");
        }
        List<?> ids = (List<?>) params.get("ids");
        if (ids != null) {
            for (Object id : ids) {
                agentService.deleteConversation(Long.valueOf(id.toString()));
            }
        }
        return ApiResponse.success();
    }

    /**
     * 清空当前用户所有对话
     */
    @DeleteMapping("/conversations/all")
    public ApiResponse<Void> deleteAll() {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error(400, "请先登录");
        }
        List<AgentConversation> conversations = agentService.listConversations(userId);
        for (AgentConversation conv : conversations) {
            agentService.deleteConversation(conv.getId());
        }
        return ApiResponse.success();
    }
}
