package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 反馈会话实体类
 *
 * @author luosg
 */
@Data
public class FeedbackSession {
    private Long id;
    private Long userId;
    private String userName;
    /** 0=open, 1=closed */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
