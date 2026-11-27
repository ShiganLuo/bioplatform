package com.bioplatform.dto.datafile;

/**
 * 样本 meta 文件引用批量状态汇总(2026-11-26 主动获取,不再等打开明细)。
 *
 * @param metaId        样本 meta ID
 * @param totalRefs     引用总数
 * @param matchedRefs   已入库（匹配到 data_files）数
 * @param lostRefs      已入库但磁盘丢失数(fileStatus=0)
 * @param unmatchedRefs 未匹配到 data_files 数
 */
public record SampleMetaFileSummary(
        Long metaId,
        int totalRefs,
        int matchedRefs,
        int lostRefs,
        int unmatchedRefs) {
}
