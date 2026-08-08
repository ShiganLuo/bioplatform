package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Agent消息实体类
 *
 * @author luosg
 */
@Data
public class AgentMessage {
    private Long id;

    private Long conversationId;

    /** 角色：user/assistant/system/tool */
    private String role;

    /** 消息内容（TEXT类型） */
    private String content;

    /** 工具调用JSON（TEXT类型） */
    private String toolCallsJson;

    private LocalDateTime createdAt;
}
