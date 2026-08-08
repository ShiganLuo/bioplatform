package com.bioplatform.controller.admin;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.OperationLog;
import com.bioplatform.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

/**
 * Admin operation log controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    private final OperationLogService operationLogService;

    public AdminLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * Paginated operation logs.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation) {
        PageResult result = operationLogService.listLogs(pageNum, pageSize, userId, operation);
        return ApiResponse.success(result);
    }
}
