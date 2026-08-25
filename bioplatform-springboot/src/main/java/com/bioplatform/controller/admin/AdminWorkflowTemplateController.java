package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.WorkflowTemplate;
import com.bioplatform.service.WorkflowTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/**
 * 工作流模板管理 Controller
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminWorkflowTemplateController {

    private final WorkflowTemplateService templateService;

    public AdminWorkflowTemplateController(WorkflowTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        PageResult result = templateService.listTemplates(type, category, page, size);
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowTemplate> getById(@PathVariable Long id) {
        WorkflowTemplate template = templateService.getTemplateById(id);
        if (template == null) {
            return ApiResponse.error(404, "模板不存在");
        }
        return ApiResponse.success(template);
    }

    @PostMapping("/create")
    @OperLog(module = "模板管理", operation = "创建模板")
    public ApiResponse<WorkflowTemplate> create(@RequestBody @Valid AdminWorkflowTemplateDTO.CreateRequest request) {
        WorkflowTemplate template = templateService.createTemplate(request);
        return ApiResponse.success(template);
    }

    @PutMapping("/update")
    @OperLog(module = "模板管理", operation = "更新模板")
    public ApiResponse<Void> update(@RequestBody @Valid AdminWorkflowTemplateDTO.UpdateRequest request) {
        templateService.updateTemplate(request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "模板管理", operation = "删除模板")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ApiResponse.success();
    }

    @PostMapping("/import")
    @OperLog(module = "模板管理", operation = "导入模板")
    public ApiResponse<Integer> importFromOmics(@RequestBody @Valid AdminWorkflowTemplateDTO.ImportRequest request) {
        int count = templateService.importFromOmics(request.omicsDir());
        return ApiResponse.success(count);
    }

    /**
     * 调试用：扫描目录返回将要导入的文件列表
     */
    @GetMapping("/debug-scan")
    public ApiResponse<Object> debugScan(@RequestParam String dir) {
        java.nio.file.Path configDir = java.nio.file.Paths.get(dir, "config");
        java.util.List<String> files = new java.util.ArrayList<>();
        java.util.List<String> skipped = new java.util.ArrayList<>();
        try (java.nio.file.DirectoryStream<java.nio.file.Path> stream =
                     java.nio.file.Files.newDirectoryStream(configDir, "*.json")) {
            for (java.nio.file.Path p : stream) {
                String name = p.getFileName().toString();
                if (name.endsWith(".schema.json") || name.equals("schema.json") || name.equals("schema.schema.json")) {
                    skipped.add(name + " (schema)");
                } else {
                    String workflowName = name.replace(".json", "");
                    java.nio.file.Path schemaFile = configDir.resolve(workflowName + ".schema.json");
                    if (!java.nio.file.Files.exists(schemaFile)) {
                        skipped.add(name + " (no schema)");
                    } else {
                        files.add(name);
                    }
                }
            }
        } catch (Exception e) {
            return ApiResponse.error(500, "扫描失败: " + e.getMessage());
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("configDir", configDir.toString());
        result.put("exists", java.nio.file.Files.isDirectory(configDir));
        result.put("willImport", files);
        result.put("willSkip", skipped);
        return ApiResponse.success(result);
    }
}
