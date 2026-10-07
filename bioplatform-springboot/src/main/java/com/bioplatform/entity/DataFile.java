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

    /** 内容 MD5（hex），秒传去重用 */
    private String contentHash;

    private String organism;

    private String genomeVersion;

    private Long projectId;

    private Long uploadedBy;

    /** 所属项目名（联表填充，非表字段） */
    private String projectName;

    /** 上传者用户名（联表填充，非表字段） */
    private String uploaderName;

    private LocalDateTime createdAt;
}
