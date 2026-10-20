package com.bioplatform.service;

import com.bioplatform.dto.datafile.SampleMetaFilesResult;

import java.util.List;

/**
 * 样本 meta 文件引用解析服务
 *
 * @author luosg
 */
public interface SampleMetaFileService {

    /**
     * 解析 meta 的 TSV 内容，提取文件引用并与 data_files 匹配。
     *
     * @param metaId 样本 meta ID
     * @return 引用明细 + 汇总计数
     */
    SampleMetaFilesResult resolveMetaFiles(Long metaId);

    /**
     * 将 meta 中未入库、但磁盘上存在的引用登记进 data_files（复用 import-local 契约）。
     *
     * @param metaId 样本 meta ID
     * @param userId 操作者ID
     * @return 登记成功的数量
     */
    int registerMissingFiles(Long metaId, Long userId);

    /**
     * 判断 TSV 单元格是否为文件引用（列名白名单 + 内容启发式）。
     * 供前端语义对齐或未来 pipeline meta 复用。
     */
    boolean isFileCell(String columnName, String value);
}
