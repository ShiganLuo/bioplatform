package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.common.util.OwnershipUtils;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.Project;
import com.bioplatform.entity.SampleMeta;
import com.bioplatform.service.ProjectService;
import com.bioplatform.service.SampleMetaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sample-meta")
public class AdminSampleMetaController {

    private final SampleMetaService sampleMetaService;
    private final ProjectService projectService;

    public AdminSampleMetaController(SampleMetaService sampleMetaService, ProjectService projectService) {
        this.sampleMetaService = sampleMetaService;
        this.projectService = projectService;
    }

    private void checkProjectOwner(Long projectId) {
        Project project = projectService.getProjectById(projectId);
        if (project != null) {
            OwnershipUtils.checkProjectOwnership(project.getOwnerId());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<SampleMeta>> list(@RequestParam Long projectId) {
        checkProjectOwner(projectId);
        return ApiResponse.success(sampleMetaService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public ApiResponse<SampleMeta> getById(@PathVariable Long id) {
        SampleMeta meta = sampleMetaService.getById(id);
        if (meta == null) return ApiResponse.error(404, "Meta不存在");
        checkProjectOwner(meta.getProjectId());
        return ApiResponse.success(meta);
    }

    @PostMapping("/create")
    @OperLog(module = "样本信息", operation = "创建样本Meta")
    public ApiResponse<SampleMeta> create(@RequestBody @Valid SampleMeta sampleMeta) {
        Long userId = LoginUserHolder.getCurrentUserId();
        sampleMeta.setCreatedBy(userId);
        sampleMetaService.create(sampleMeta);
        return ApiResponse.success(sampleMeta);
    }

    @PutMapping("/update")
    @OperLog(module = "样本信息", operation = "更新样本Meta")
    public ApiResponse<Void> update(@RequestBody @Valid SampleMeta sampleMeta) {
        if (sampleMeta.getId() == null) return ApiResponse.error(400, "id不能为空");
        SampleMeta existing = sampleMetaService.getById(sampleMeta.getId());
        if (existing != null) checkProjectOwner(existing.getProjectId());
        sampleMetaService.update(sampleMeta);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @OperLog(module = "样本信息", operation = "删除样本Meta")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SampleMeta existing = sampleMetaService.getById(id);
        if (existing != null) checkProjectOwner(existing.getProjectId());
        sampleMetaService.delete(id);
        return ApiResponse.success();
    }
}
