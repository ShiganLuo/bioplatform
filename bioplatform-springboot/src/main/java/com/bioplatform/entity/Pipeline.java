package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线实体类
 *
 * @author luosg
 */
@Data
public class Pipeline {
    private Long id;

    private String name;

    private String description;

    private String category;

    /** 配置JSON（TEXT类型） */
    private String configJson;

    private String dockerImage;

    /** 超时时间（秒） */
    private Integer timeout;

    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
