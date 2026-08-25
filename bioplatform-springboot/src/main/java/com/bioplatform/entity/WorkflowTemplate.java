package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工作流模板实体类
 *
 * @author luosg
 */
@Data
public class WorkflowTemplate {
    private Long id;
    private String name;
    private String description;
    /** 类型：task / pipeline */
    private String type;
    /** 分类：转录组、变异检测、表观遗传学等 */
    private String category;
    /** 默认配置 JSON */
    private String configTemplate;
    /** 表单 schema JSON */
    private String schemaJson;
    /** 相对于 Omics 仓库的 .smk 路径 */
    private String snakemakePath;
    private String icon;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
