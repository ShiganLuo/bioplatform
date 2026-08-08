package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.SystemConfig;
import com.bioplatform.service.SystemService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin system configuration and dashboard controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final SystemService systemService;

    public AdminSystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * List all system configurations.
     */
    @GetMapping("/configs")
    public ApiResponse<List<SystemConfig>> listConfigs() {
        List<SystemConfig> configs = systemService.getAllConfigs();
        return ApiResponse.success(configs);
    }

    /**
     * Update a config value.
     */
    @PutMapping("/configs")
    @OperLog(module = "系统管理", operation = "更新系统配置")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, String> params) {
        String key = params.get("key");
        String value = params.get("value");
        if (key == null || key.isBlank()) {
            return ApiResponse.error(400, "配置键不能为空");
        }
        systemService.updateConfig(key, value);
        return ApiResponse.success();
    }

    /**
     * Get dashboard statistics.
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> stats = systemService.getDashboardStats();
        return ApiResponse.success(stats);
    }
}
