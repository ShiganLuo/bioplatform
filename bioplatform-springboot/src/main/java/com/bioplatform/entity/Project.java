package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目实体类
 *
 * @author luosg
 */
@Data
public class Project {
    private Long id;

    private String name;

    private String description;

    /** 物种 */
    private String organism;

    /** 基因组版本 */
    private String genomeVersion;

    private Long ownerId;

    /** 状态：0=归档 1=活跃 */
    private Integer status;

    /** 是否私有：true=私有 false=公开 */
    private Boolean isPrivate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
