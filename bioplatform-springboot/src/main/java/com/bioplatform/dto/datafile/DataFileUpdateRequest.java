package com.bioplatform.dto.datafile;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 数据文件编辑请求（文件-项目多对多归属）
 * <p>
 * name / organism / genomeVersion 为 null 表示不修改；
 * projectIds 为全量替换的目标归属，至少一个（否则文件在任何项目中都不可见）。
 * 物理文件、path、fileSize、fileType、contentHash 一律不可编辑。
 * </p>
 *
 * @param name          文件显示名（同时作为下载文件名，不重命名物理文件）
 * @param organism      物种
 * @param genomeVersion 基因组版本
 * @param projectIds    所属项目ID列表（全量替换）
 */
public record DataFileUpdateRequest(
        String name,
        String organism,
        String genomeVersion,
        @NotEmpty(message = "所属项目至少选择一个") List<Long> projectIds) {
}
