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

    /** 类型：task / pipeline */
    private String type;

    /** 关联 workflow_templates.id */
    private Long templateId;

    /** 关联 projects.id */
    private Long projectId;

    /** meta TSV内容或服务器路径 */
    private String metaContent;

    /** meta类型：text/path */
    private String metaType;

    /** 实验设计 design JSON(2026-11-26 换代):样本信息"填入"时随 meta 一并带入,
     *  执行时落 design.json 传 run.py --design(唯一入口);形状同 sample_params.design */
    private String designJson;

    /** 用户覆盖的额外参数JSON */
    private String extraParams;

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

    // ===== 列表展示用非表字段(2026-11-26 版本列,批量回填) =====
    /** 最近一次执行的流程版本(Omics commit) */
    private String latestWorkflowVersion;
    /** 最近执行版本来源: git=派发采集 / inferred=历史推断 / unknown */
    private String latestVersionSource;
    /** 模板导入时的 Omics 版本 */
    private String templateWorkflowVersion;
}
