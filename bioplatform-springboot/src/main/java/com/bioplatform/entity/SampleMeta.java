package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 样本元信息实体类
 */
@Data
public class SampleMeta {
    private Long id;
    private Long projectId;
    private String name;
    private String metaMode;
    private String metaContent;
    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
