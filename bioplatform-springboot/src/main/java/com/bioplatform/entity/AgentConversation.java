package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Agent对话实体类
 *
 * @author luosg
 */
@Data
public class AgentConversation {
    private Long id;

    private Long userId;

    private Long projectId;

    private String title;

    private String modelName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
