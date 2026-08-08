package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体类
 *
 * @author luosg
 */
@Data
public class SystemConfig {
    private Long id;

    private String configKey;

    private String configValue;

    private String configDesc;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
