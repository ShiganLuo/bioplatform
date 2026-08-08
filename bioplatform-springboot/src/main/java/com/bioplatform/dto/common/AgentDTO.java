package com.bioplatform.dto.common;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Agent conversation DTOs.
 */
public final class AgentDTO {

    private AgentDTO() {
        // utility class
    }

    /**
     * Agent conversation session.
     */
    public record AgentConversationDTO(
            String conversationId,
            String title,
            Long userId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * Agent message within a conversation.
     */
    public record AgentMessageDTO(
            Long id,
            String conversationId,
            String role,
            String content,
            List<AgentToolDTO> toolCalls,
            LocalDateTime createdAt
    ) {
    }

    /**
     * Agent chat request from the client.
     */
    public record AgentChatRequest(
            String message,
            String conversationId
    ) {
    }

    /**
     * Agent chat response to the client.
     */
    public record AgentChatResponse(
            String conversationId,
            String content,
            List<AgentToolDTO> toolCalls,
            Boolean finished
    ) {
    }

    /**
     * Agent tool invocation detail.
     */
    public record AgentToolDTO(
            String toolName,
            String toolInput,
            String toolOutput
    ) {
    }
}
