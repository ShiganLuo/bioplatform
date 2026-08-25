package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineCreateRequest;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin pipeline management controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/pipelines")
public class AdminPipelineController {

    private final com.bioplatform.service.PipelineService pipelineService;

    public AdminPipelineController(com.bioplatform.service.PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Paginated pipeline list, optionally filtered by category.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        PageResult result = pipelineService.listPipelines(category, page, size);
        return ApiResponse.success(result);
    }

    /**
     * Get pipeline by id.
     */
    @GetMapping("/{id}")
    public ApiResponse<Pipeline> getById(@PathVariable Long id) {
        Pipeline pipeline = pipelineService.getPipelineById(id);
        if (pipeline == null) {
            return ApiResponse.error(404, "流水线不存在");
        }
        return ApiResponse.success(pipeline);
    }

    /**
     * Create a pipeline.
     */
    @PostMapping("/create")
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
    @OperLog(module = "流水线管理", operation = "更新流水线")
    public ApiResponse<Void> update(@RequestBody @Valid com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineUpdateRequest request) {
        pipelineService.updatePipeline(request.id(),
                new AdminPipelineCreateRequest(request.name(), request.type(), request.templateId(),
                        request.projectId(), request.metaContent(), request.metaType(), request.extraParams(),
                        request.description(), request.category(),
                        request.configJson(), request.dockerImage(), request.timeout()));
        return ApiResponse.success();
    }

    /**
     * Delete a pipeline.
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "流水线管理", operation = "删除流水线")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        pipelineService.deletePipeline(id);
        return ApiResponse.success();
    }

    /**
     * Execute a pipeline.
     */
    @PostMapping("/{id}/execute")
    @OperLog(module = "流水线管理", operation = "执行流水线")
    public ApiResponse<PipelineExecution> execute(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, Object> params) {
        Long userId = LoginUserHolder.getCurrentUserId();
        Long projectId = params != null && params.get("projectId") != null
                ? Long.valueOf(params.get("projectId").toString()) : null;
        String inputParams = params != null && params.get("inputParams") != null
                ? params.get("inputParams").toString() : null;
        PipelineExecution execution = pipelineService.executePipeline(id, projectId, inputParams, userId);
        return ApiResponse.success(execution);
    }
}
