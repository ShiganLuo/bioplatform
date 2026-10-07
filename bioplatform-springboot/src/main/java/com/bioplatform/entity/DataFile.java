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

    private Long uploadedBy;

    /** 查询过滤参数（project_id 表列已迁移至 data_file_projects，此字段仅作查询条件，不落库不回填） */
    private Long projectId;

    /** 所属项目ID（逗号分隔，联表子查询填充，非表字段） */
    private String projectIds;

    /** 所属项目名（顿号分隔，联表子查询填充，非表字段） */
    private String projectNames;

    /** 上传者用户名（联表填充，非表字段） */
    private String uploaderName;

    private LocalDateTime createdAt;
}
