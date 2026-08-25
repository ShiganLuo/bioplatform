package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
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
 * Admin AI Agent controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/agent")
public class AdminAgentController {

    private final AgentService agentService;

    public AdminAgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * List user conversations.
     */
    @GetMapping("/conversations")
    public ApiResponse<List<AgentConversation>> listConversations() {
        Long userId = LoginUserHolder.getCurrentUserId();
        List<AgentConversation> conversations = agentService.listConversations(userId);
        return ApiResponse.success(conversations);
    }

    /**
     * Create a new conversation.
     */
    @PostMapping("/conversations")
    @OperLog(module = "AI Agent管理", operation = "创建对话")
    public ApiResponse<AgentConversation> createConversation(@RequestBody Map<String, Object> params) {
        Long userId = LoginUserHolder.getCurrentUserId();
        Long projectId = params.get("projectId") != null ? Long.valueOf(params.get("projectId").toString()) : null;
        String title = params.getOrDefault("title", "新对话").toString();
        String modelName = params.getOrDefault("modelName", "gpt-4").toString();

        AgentConversation conversation = agentService.createConversation(userId, projectId, title, modelName);
        return ApiResponse.success(conversation);
    }

    /**
     * Delete a conversation.
     */
    @DeleteMapping("/conversations/{id}")
    @OperLog(module = "AI Agent管理", operation = "删除对话")
    public ApiResponse<Void> deleteConversation(@PathVariable Long id) {
        agentService.deleteConversation(id);
        return ApiResponse.success();
    }

    /**
     * 批量删除对话
     */
    @PostMapping("/conversations/batch-delete")
    @OperLog(module = "AI Agent管理", operation = "批量删除对话")
    public ApiResponse<Void> batchDelete(@RequestBody Map<String, Object> params) {
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
    @OperLog(module = "AI Agent管理", operation = "清空所有对话")
    public ApiResponse<Void> deleteAll() {
        Long userId = LoginUserHolder.getCurrentUserId();
        List<AgentConversation> conversations = agentService.listConversations(userId);
        for (AgentConversation conv : conversations) {
            agentService.deleteConversation(conv.getId());
        }
        return ApiResponse.success();
    }

    /**
     * Get messages for a conversation.
     */
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AgentMessage>> getMessages(@PathVariable Long id) {
        List<AgentMessage> messages = agentService.getMessages(id);
        return ApiResponse.success(messages);
    }

    /**
     * Send a chat message and get AI response.
     */
    @PostMapping("/chat")
    @OperLog(module = "AI Agent管理", operation = "发送消息")
    public ApiResponse<AgentMessage> chat(@RequestBody Map<String, Object> params) {
        Long userId = LoginUserHolder.getCurrentUserId();

        Long conversationId = null;
        Object convIdObj = params.get("conversationId");
        if (convIdObj != null && !convIdObj.toString().isBlank()) {
            conversationId = Long.valueOf(convIdObj.toString());
        }

        // 兼容前端传 message 或 content
        Object contentObj = params.get("content");
        if (contentObj == null) {
            contentObj = params.get("message");
        }
        if (contentObj == null || contentObj.toString().isBlank()) {
            return ApiResponse.error(400, "消息内容不能为空");
        }
        String content = contentObj.toString();

        // 若无 conversationId，自动创建新对话
        if (conversationId == null) {
            AgentConversation conversation =
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
        Long userId = LoginUserHolder.getCurrentUserId();

        Long conversationId = null;
        Object convIdObj = params.get("conversationId");
        if (convIdObj != null && !convIdObj.toString().isBlank()) {
            conversationId = Long.valueOf(convIdObj.toString());
        }

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
        String content = contentObj.toString();

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
}
