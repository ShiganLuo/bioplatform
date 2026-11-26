package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.common.util.OwnershipUtils;
import com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineCreateRequest;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Admin pipeline management controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/pipelines")
public class AdminPipelineController {

    private final com.bioplatform.service.PipelineService pipelineService;
    private final com.bioplatform.service.BenchmarkService benchmarkService;

    public AdminPipelineController(com.bioplatform.service.PipelineService pipelineService,
                                   com.bioplatform.service.BenchmarkService benchmarkService) {
        this.pipelineService = pipelineService;
        this.benchmarkService = benchmarkService;
    }

    /**
     * Paginated pipeline list, optionally filtered by category.
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('pipeline:list')")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        PageResult result;
        if (OwnershipUtils.isAdmin()) {
            result = pipelineService.listPipelines(category, page, size);
        } else {
            Long userId = OwnershipUtils.getCurrentUserId();
            result = pipelineService.listPipelinesByOwner(userId, category, page, size);
        }
        return ApiResponse.success(result);
    }

    /**
     * Get pipeline by id.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pipeline:list')")
    public ApiResponse<Pipeline> getById(@PathVariable Long id) {
        Pipeline pipeline = pipelineService.getPipelineById(id);
        if (pipeline == null) {
            return ApiResponse.error(404, "流水线不存在");
        }
        OwnershipUtils.checkOwnership(pipeline.getOwnerId(), "流水线");
        return ApiResponse.success(pipeline);
    }

    /**
     * Create a pipeline.
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('pipeline:create')")
    @OperLog(module = "流水线管理", operation = "创建流水线")
    public ApiResponse<Pipeline> create(@RequestBody @Valid AdminPipelineCreateRequest request) {
        Long userId = LoginUserHolder.getCurrentUserId();
        Pipeline pipeline = pipelineService.createPipeline(request, userId);
        return ApiResponse.success(pipeline);
    }

    /**
     * Update a pipeline.
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('pipeline:edit')")
    @OperLog(module = "流水线管理", operation = "更新流水线")
    public ApiResponse<Void> update(@RequestBody @Valid com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineUpdateRequest request) {
        Pipeline existing = pipelineService.getPipelineById(request.id());
        if (existing == null) return ApiResponse.error(404, "流水线不存在");
        OwnershipUtils.checkOwnership(existing.getOwnerId(), "流水线");
        pipelineService.updatePipeline(request.id(),
                new AdminPipelineCreateRequest(request.name(), request.type(), request.templateId(),
                        request.projectId(), request.metaContent(), request.metaType(), request.extraParams(),
                        request.description(), request.category(),
                        request.configJson(), request.dockerImage(), request.timeout(), request.comparisonJson()));
        return ApiResponse.success();
    }

    /**
     * Delete a pipeline.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('pipeline:delete')")
    @OperLog(module = "流水线管理", operation = "删除流水线")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Pipeline existing = pipelineService.getPipelineById(id);
        if (existing == null) return ApiResponse.error(404, "流水线不存在");
        OwnershipUtils.checkOwnership(existing.getOwnerId(), "流水线");
        pipelineService.deletePipeline(id);
        return ApiResponse.success();
    }

    /**
     * Execute a pipeline.
     */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('pipeline:run')")
    @OperLog(module = "流水线管理", operation = "执行流水线")
    public ApiResponse<PipelineExecution> execute(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> params) {
        Long userId = LoginUserHolder.getCurrentUserId();
        Long projectId = params != null && params.get("projectId") != null
                ? Long.valueOf(params.get("projectId").toString()) : null;
        String inputParams = params != null && params.get("inputParams") != null
                ? params.get("inputParams").toString() : null;
        if (projectId == null) {
            // 请求体不带 projectId 时回落到流水线绑定的项目(project_id 列 NOT NULL)
            var pipeline = pipelineService.getPipelineById(id);
            projectId = pipeline != null ? pipeline.getProjectId() : null;
        }
        PipelineExecution execution = pipelineService.executePipeline(id, projectId, inputParams, userId);
        return ApiResponse.success(execution);
    }

    /**
     * 分析维护操作(2026-11-24 方案二):dry-run 预检 / unlock 解锁 / delete-incomplete
     * 清理不完整输出。即时执行返回输出文本,不建执行记录(本注解即审计)。
     */
    @PostMapping("/{id}/maintain")
    @PreAuthorize("hasAuthority('pipeline:run')")
    @OperLog(module = "流水线管理", operation = "分析维护")
    public ApiResponse<String> maintain(@PathVariable Long id,
                                        @RequestBody Map<String, String> params) {
        String mode = params == null ? null : params.get("mode");
        String extra = params == null ? null : params.get("extra");
        return ApiResponse.success(pipelineService.maintainPipeline(id, mode, extra));
    }

    /**
     * 资源校准两段式(2026-11-26 方案三A):apply=false 预览建议表,
     * true 时护栏(n≥3/±30%/取整/删失下界)写回 Omics config。
     * body: {apply?: bool, force?: bool}(force=确认混入未提交改动)。
     */
    @PostMapping("/{id}/calibrate")
    @PreAuthorize("hasAuthority('pipeline:run')")
    @OperLog(module = "流水线管理", operation = "资源校准")
    public ApiResponse<com.bioplatform.dto.admin.AdminBenchmarkDTO.CalibrateResult> calibrate(
            @PathVariable Long id, @RequestBody Map<String, Object> params) {
        boolean apply = params != null && Boolean.TRUE.equals(params.get("apply"));
        boolean force = params != null && Boolean.TRUE.equals(params.get("force"));
        return ApiResponse.success(benchmarkService.calibrate(id, apply, force));
    }
}
