package com.bioplatform.controller.admin;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.OperationLog;
import com.bioplatform.service.OperationLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin operation log controller.
 *
 * @author luosg
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    private final OperationLogService operationLogService;

    public AdminLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * Paginated operation logs with filters.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        OperationLog filter = new OperationLog();
        filter.setUserId(userId);
        filter.setUsername(username);
        filter.setModule(module);
        filter.setStatus(status);
        filter.setOperation(operation);
        filter.setKeyword(keyword);
        filter.setStartDate(startDate);
        filter.setEndDate(endDate);
        PageResult result = operationLogService.listLogs(page, size, filter);
        return ApiResponse.success(result);
    }
}
