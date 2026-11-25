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

    /** 实验设计关系 JSON(2026-11-25):comparison 列表,Omics --comparison 文件同构
     *  [{comparison_id, control_group_name?, experiment_group_name?,
     *    control_samples[], experiment_samples[]}] */
    private String comparisonJson;

    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
