package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.service.PipelineExecutionService;
import org.springframework.web.bind.annotation.*;

/**
 * Admin pipeline execution controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/executions")
public class AdminExecutionController {

    private final PipelineExecutionService pipelineExecutionService;

    public AdminExecutionController(PipelineExecutionService pipelineExecutionService) {
        this.pipelineExecutionService = pipelineExecutionService;
    }

    /**
     * Paginated execution list.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long userId) {
        PageResult result;
        if (userId != null) {
            result = pipelineExecutionService.listByUserId(userId, pageNum, pageSize);
        } else if (projectId != null) {
            result = pipelineExecutionService.listByProjectId(projectId, pageNum, pageSize);
        } else {
            // Default: list all (could be filtered by admin's own projects)
            result = pipelineExecutionService.listByUserId(null, pageNum, pageSize);
        }
        return ApiResponse.success(result);
    }

    /**
     * Get execution by id.
     */
    @GetMapping("/{id}")
    public ApiResponse<PipelineExecution> getById(@PathVariable Long id) {
        PipelineExecution execution = pipelineExecutionService.getExecutionById(id);
        if (execution == null) {
            return ApiResponse.error(404, "执行记录不存在");
        }
        return ApiResponse.success(execution);
    }

    /**
     * Cancel an execution.
     */
    @PutMapping("/{id}/cancel")
    @OperLog(module = "执行管理", operation = "取消执行")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        pipelineExecutionService.cancelExecution(id);
        return ApiResponse.success();
    }
}
