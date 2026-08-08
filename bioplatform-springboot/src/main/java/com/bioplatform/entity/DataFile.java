package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据文件实体类
 *
 * @author luosg
 */
@Data
public class DataFile {
    private Long id;

    private String name;

    private String path;

    private String fileType;

    private Long fileSize;

    private String organism;

    private String genomeVersion;

    private Long projectId;

    private Long uploadedBy;

    private LocalDateTime createdAt;
}
