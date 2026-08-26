package com.bioplatform.controller.admin;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.worker.WorkerRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 计算节点管理接口
 */
@RestController
@RequestMapping("/api/admin/workers")
public class AdminWorkerController {

    private final WorkerRegistry workerRegistry;

    public AdminWorkerController(WorkerRegistry workerRegistry) {
        this.workerRegistry = workerRegistry;
    }

    /**
     * 获取所有节点列表
     */
    @GetMapping
    public ApiResponse<List<WorkerRegistry.WorkerInfo>> listWorkers() {
        return ApiResponse.success(workerRegistry.getAllWorkers());
    }

    /**
     * 添加计算节点
     */
    @PostMapping
    public ApiResponse<WorkerRegistry.WorkerInfo> addWorker(@RequestBody Map<String, String> params) {
        String url = params.get("url");
        String hostname = params.get("hostname");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "节点地址不能为空");
        }
        // 格式化 URL
        url = url.replaceAll("/+$", "");
        WorkerRegistry.WorkerInfo info = workerRegistry.addNode(url, hostname);
        return ApiResponse.success(info);
    }

    /**
     * 删除计算节点
     */
    @DeleteMapping("/{nodeId}")
    public ApiResponse<Void> removeWorker(@PathVariable String nodeId) {
        workerRegistry.removeNode(nodeId);
        return ApiResponse.success();
    }

    /**
     * 启用/禁用节点
     */
    @PutMapping("/{nodeId}/status")
    public ApiResponse<Void> setEnabled(@PathVariable String nodeId, @RequestBody Map<String, Boolean> params) {
        Boolean enabled = params.get("enabled");
        workerRegistry.setNodeEnabled(nodeId, enabled != null && enabled);
        return ApiResponse.success();
    }

    /**
     * 测试节点连接
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody Map<String, String> params) {
        String url = params.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "节点地址不能为空");
        }
        boolean ok = workerRegistry.testConnection(url.replaceAll("/+$", ""));
        if (ok) {
            return ApiResponse.success(Map.of("connected", true, "message", "连接成功"));
        } else {
            return ApiResponse.success(Map.of("connected", false, "message", "连接失败，请检查地址和网络"));
        }
    }
}
