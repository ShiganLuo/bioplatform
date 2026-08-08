package com.bioplatform.controller.front;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.AgentMessage;
import com.bioplatform.entity.AgentTool;
import com.bioplatform.service.AgentService;
import org.springframework.web.bind.annotation.*;

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
        // For anonymous users, use a default user ID (0 or handle in service)
        if (userId == null) {
            userId = 0L;
        }

        Long conversationId = params.get("conversationId") != null
                ? Long.valueOf(params.get("conversationId").toString()) : null;
        String content = params.get("content").toString();

        // If no conversation ID, create a new conversation first
        if (conversationId == null) {
            com.bioplatform.entity.AgentConversation conversation =
                    agentService.createConversation(userId, null, "新对话", "gpt-4");
            conversationId = conversation.getId();
        }

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
