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

    /** 实验设计 design JSON(2026-11-26 换代,run.py --design 唯一入口):
     *  {tissue: {groups:{组名:[样本]}, comparisons:{对比名:{groups,ref?,coding?}}, tests?}} */
    private String designJson;

    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
