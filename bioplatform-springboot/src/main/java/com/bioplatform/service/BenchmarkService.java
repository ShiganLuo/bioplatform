package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminBenchmarkDTO.*;
import com.bioplatform.dto.common.PageResult;

import java.util.List;

/**
 * benchmark 性能分析与资源校准服务(2026-11-26)
 *
 * @author luosg
 */
public interface BenchmarkService {

    /** 规则聚合(按 规则×版本;版本缺省=当前 HEAD,"ALL"=不筛) */
    List<SummaryRow> summary(Long pipelineId, String workflow, String version);

    /** 该流水线出现过的版本列表(各版本执行数/数据点数) */
    List<VersionInfo> versions(Long pipelineId);

    /** 明细分页(逐 job/attempt) */
    PageResult<com.bioplatform.entity.PipelineBenchmark> list(Long pipelineId, String rule,
                                                              Long executionId, String version,
                                                              int pageNum, int pageSize);

    /** 跨执行时序(趋势图) */
    List<com.bioplatform.entity.PipelineBenchmark> trend(Long pipelineId, String rule);

    /** 历史回填:终态执行收割 + 版本推断(git log --before) */
    BackfillResult backfill(Long pipelineId);

    /** 资源校准两段式:apply=false 预览建议表,true 护栏写回 config */
    CalibrateResult calibrate(Long pipelineId, boolean apply, boolean force);
}
