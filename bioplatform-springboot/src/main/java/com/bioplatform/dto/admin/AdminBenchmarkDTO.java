package com.bioplatform.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * benchmark 性能分析与资源校准 DTO(2026-11-26 方案一/二/三A)
 *
 * @author luosg
 */
public final class AdminBenchmarkDTO {

    private AdminBenchmarkDTO() {
    }

    /** 规则聚合行(按 规则×版本) */
    public record SummaryRow(String ruleName, String workflowVersion, Integer n, Integer nCensored,
                             Double rssP50Mb, Double rssP90Mb, Double rssMaxMb,
                             Double secP50, Double secP90,
                             Double suggestMb, Object declaredMb, Double deltaPct,
                             String note) {
    }

    /** 版本分布行 */
    public record VersionInfo(String version, Integer execCount, Integer pointCount) {
    }

    /** 校准建议行(与 calibrate_resources.py 的 resources_suggest.tsv 同构) */
    public record SuggestRow(String rule, Integer nOk, Integer nCensored,
                             String observedP90Mb, String suggestMb,
                             Object prevDeclaredMb, String deltaPct, String note) {
    }

    /** 校准结果(预览/写回两段共用) */
    public record CalibrateResult(String version, boolean applied, int historyRows,
                                  List<SuggestRow> rows, String diff, String log) {
    }

    /** 回填结果 */
    public record BackfillResult(int executions, int inferred, int unknown, int rowsInserted,
                                 Map<String, Object> detail) {
    }
}
