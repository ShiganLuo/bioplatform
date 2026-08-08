package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.AgentConversation;
import com.bioplatform.entity.AgentMessage;
import com.bioplatform.entity.AgentTool;
import com.bioplatform.service.AgentService;
import org.springframework.web.bind.annotation.*;

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
        Long conversationId = Long.valueOf(params.get("conversationId").toString());
        String content = params.get("content").toString();

        AgentMessage response = agentService.sendMessage(conversationId, content, userId);
        return ApiResponse.success(response);
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
