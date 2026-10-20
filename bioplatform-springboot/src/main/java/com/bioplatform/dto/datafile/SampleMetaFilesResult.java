package com.bioplatform.dto.datafile;

import java.util.List;

/**
 * 样本 meta 的文件引用解析汇总
 *
 * @param metaId      样本 meta ID
 * @param refs        逐条引用明细
 * @param totalRefs   引用总数
 * @param matchedRefs 已入库（匹配到 data_files）的引用数
 */
public record SampleMetaFilesResult(
        Long metaId,
        List<SampleMetaFileRef> refs,
        int totalRefs,
        int matchedRefs) {
}
