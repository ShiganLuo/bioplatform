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
}
