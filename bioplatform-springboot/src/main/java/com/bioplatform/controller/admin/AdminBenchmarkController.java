package com.bioplatform.controller.admin;

import com.bioplatform.dto.admin.AdminBenchmarkDTO.*;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.PipelineBenchmark;
import com.bioplatform.service.BenchmarkService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * benchmark 性能分析(方案二)+ 历史回填(方案一)。
 * 权限并入执行域:读=execution:list,回填(经 worker 收集)=pipeline:run。
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/benchmarks")
public class AdminBenchmarkController {

    private final BenchmarkService benchmarkService;

    public AdminBenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /** 规则聚合(按 规则×版本;version 缺省=当前 HEAD,ALL=不筛) */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('execution:list')")
    public ApiResponse<List<SummaryRow>> summary(@RequestParam Long pipelineId,
                                                 @RequestParam(required = false) String workflow,
                                                 @RequestParam(required = false) String version) {
        return ApiResponse.success(benchmarkService.summary(pipelineId, workflow, version));
    }

    /** 版本列表(各版本执行数/数据点数) */
    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('execution:list')")
    public ApiResponse<List<VersionInfo>> versions(@RequestParam Long pipelineId) {
        return ApiResponse.success(benchmarkService.versions(pipelineId));
    }

    /** 明细分页 */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('execution:list')")
    public ApiResponse<PageResult<PipelineBenchmark>> list(@RequestParam Long pipelineId,
                                                           @RequestParam(required = false) String rule,
                                                           @RequestParam(required = false) Long executionId,
                                                           @RequestParam(required = false) String version,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(benchmarkService.list(pipelineId, rule, executionId, version, page, pageSize));
    }

    /** 跨执行时序(趋势图,版本分段) */
    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('execution:list')")
    public ApiResponse<List<PipelineBenchmark>> trend(@RequestParam Long pipelineId,
                                                      @RequestParam(required = false) String rule) {
        return ApiResponse.success(benchmarkService.trend(pipelineId, rule));
    }

    /** 历史回填:终态执行收割 + 版本推断 */
    @PostMapping("/backfill")
    @PreAuthorize("hasAuthority('pipeline:run')")
    public ApiResponse<BackfillResult> backfill(@RequestParam Long pipelineId) {
        return ApiResponse.success(benchmarkService.backfill(pipelineId));
    }
}
