package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.OwnershipUtils;
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long userId) {
        PageResult result;
        if (OwnershipUtils.isAdmin()) {
            // 管理员：可按userId/projectId过滤，或查看全部
            if (userId != null) {
                result = pipelineExecutionService.listByUserId(userId, page, size);
            } else if (projectId != null) {
                result = pipelineExecutionService.listByProjectId(projectId, page, size);
            } else {
                result = pipelineExecutionService.listByUserId(null, page, size);
            }
        } else {
            // 普通用户：只能看自己的执行记录
            Long currentUserId = OwnershipUtils.getCurrentUserId();
            result = pipelineExecutionService.listByUserId(currentUserId, page, size);
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
        OwnershipUtils.checkOwnership(execution.getUserId(), "执行记录");
        return ApiResponse.success(execution);
    }

    /**
     * Cancel an execution.
     */
    @PutMapping("/{id}/cancel")
    @OperLog(module = "执行管理", operation = "取消执行")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        PipelineExecution execution = pipelineExecutionService.getExecutionById(id);
        if (execution == null) return ApiResponse.error(404, "执行记录不存在");
        OwnershipUtils.checkOwnership(execution.getUserId(), "执行记录");
        pipelineExecutionService.cancelExecution(id);
        return ApiResponse.success();
    }

    /**
     * Get execution logs.
     */
    @GetMapping("/{id}/logs")
    public ApiResponse<String> getLogs(@PathVariable Long id) {
        PipelineExecution execution = pipelineExecutionService.getExecutionById(id);
        if (execution == null) return ApiResponse.error(404, "执行记录不存在");
        OwnershipUtils.checkOwnership(execution.getUserId(), "执行记录");
        String logs = pipelineExecutionService.getExecutionLogs(id);
        return ApiResponse.success(logs);
    }
}
