package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈消息实体类
 *
 * @author luosg
 */
@Data
public class FeedbackMessage {
    private Long id;
    private Long sessionId;
    /** sender type: user/admin/system */
    private String senderType;
    private String senderName;
    private String content;
    private LocalDateTime createdAt;
}
