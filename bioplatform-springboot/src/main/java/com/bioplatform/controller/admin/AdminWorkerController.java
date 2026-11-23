package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.service.NodeMetricsService;
import com.bioplatform.worker.WorkerRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final NodeMetricsService nodeMetricsService;

    public AdminWorkerController(WorkerRegistry workerRegistry, NodeMetricsService nodeMetricsService) {
        this.workerRegistry = workerRegistry;
        this.nodeMetricsService = nodeMetricsService;
    }

    /**
     * 获取所有节点列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('worker:list')")
    public ApiResponse<List<WorkerRegistry.WorkerInfo>> listWorkers() {
        return ApiResponse.success(workerRegistry.getAllWorkers());
    }

    /**
     * 节点状态指标（应用节点 + 计算节点，实时采集）
     * 供计算节点管理负载列与主机监控页使用
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('worker:list')")
    public ApiResponse<List<Map<String, Object>>> nodeMetrics() {
        return ApiResponse.success(nodeMetricsService.buildNodeCards());
    }

    /**
     * 添加计算节点
     */
    @PostMapping
    @PreAuthorize("hasAuthority('worker:create')")
    @OperLog(module = "计算节点", operation = "添加节点")
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
    @PreAuthorize("hasAuthority('worker:delete')")
    @OperLog(module = "计算节点", operation = "删除节点")
    public ApiResponse<Void> removeWorker(@PathVariable String nodeId) {
        workerRegistry.removeNode(nodeId);
        return ApiResponse.success();
    }

    /**
     * 更新计算节点信息（URL/主机名）
     */
    @PutMapping("/{nodeId}")
    @PreAuthorize("hasAuthority('worker:edit')")
    @OperLog(module = "计算节点", operation = "更新节点")
    public ApiResponse<WorkerRegistry.WorkerInfo> updateWorker(@PathVariable String nodeId,
                                                                @RequestBody Map<String, String> params) {
        String url = params.get("url");
        String hostname = params.get("hostname");
        WorkerRegistry.WorkerInfo info = workerRegistry.updateNode(nodeId, url, hostname);
        if (info == null) {
            return ApiResponse.error(404, "节点不存在");
        }
        return ApiResponse.success(info);
    }

    /**
     * 启用/禁用节点
     */
    @PutMapping("/{nodeId}/status")
    @PreAuthorize("hasAuthority('worker:edit')")
    @OperLog(module = "计算节点", operation = "启用/禁用节点")
    public ApiResponse<Void> setEnabled(@PathVariable String nodeId, @RequestBody Map<String, Boolean> params) {
        Boolean enabled = params.get("enabled");
        workerRegistry.setNodeEnabled(nodeId, enabled != null && enabled);
        return ApiResponse.success();
    }

    /**
     * 测试节点连接
     */
    @PostMapping("/test")
    @PreAuthorize("hasAuthority('worker:edit')")
    public ApiResponse<Map<String, Object>> testConnection(@RequestBody Map<String, String> params) {
        String url = params.get("url");
        if (url == null || url.isBlank()) {
            return ApiResponse.error(400, "节点地址不能为空");
        }
        url = url.replaceAll("/+$", "");
        boolean ok = workerRegistry.testConnection(url);
        // 如果 URL 匹配已注册节点，更新其健康状态
        String testUrl = url;
        workerRegistry.getAllWorkers().stream()
                .filter(w -> w.getUrl().equals(testUrl))
                .findFirst()
                .ifPresent(w -> workerRegistry.updateNodeHealth(w.getId(), ok));
        if (ok) {
            return ApiResponse.success(Map.of("connected", true, "message", "连接成功"));
        } else {
            return ApiResponse.success(Map.of("connected", false, "message", "连接失败，请检查地址和网络"));
        }
    }
}
