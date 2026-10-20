package com.bioplatform.dto.datafile;

/**
 * 样本 meta 中单条文件引用的解析结果
 *
 * @param rawPath    TSV 单元格原始值
 * @param matched    是否匹配到 data_files 记录
 * @param fileId     匹配到的 data_files.id（未匹配为 null）
 * @param fileName   匹配到的文件名（未匹配为 null）
 * @param fileStatus 匹配到的文件状态（0=丢失 1=在库，未匹配为 null）
 * @param onDisk     rawPath 本身在磁盘上是否存在（仅绝对路径探测，未匹配且 onDisk=true 可登记入库）
 */
public record SampleMetaFileRef(
        String rawPath,
        boolean matched,
        Long fileId,
        String fileName,
        Integer fileStatus,
        boolean onDisk) {
}
