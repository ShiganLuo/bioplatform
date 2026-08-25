package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectCreateRequest;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectUpdateRequest;
import com.bioplatform.dto.admin.AdminPipelineDTO.CreateAnalysisRequest;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.Project;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Admin project management controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/projects")
public class AdminProjectController {

    private final com.bioplatform.service.ProjectService projectService;
    private final com.bioplatform.service.PipelineService pipelineService;

    public AdminProjectController(com.bioplatform.service.ProjectService projectService,
                                  com.bioplatform.service.PipelineService pipelineService) {
        this.projectService = projectService;
        this.pipelineService = pipelineService;
    }

    /**
     * Paginated project list.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = LoginUserHolder.getCurrentUserId();
        PageResult<Project> result = projectService.listUserProjects(userId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * Get project by id.
     */
    @GetMapping("/{id}")
    public ApiResponse<Project> getById(@PathVariable Long id) {
        Project project = projectService.getProjectById(id);
        if (project == null) {
            return ApiResponse.error(404, "项目不存在");
        }
        return ApiResponse.success(project);
    }

    /**
     * Create a project.
     */
    @PostMapping("/create")
    @OperLog(module = "项目管理", operation = "创建项目")
    public ApiResponse<Project> create(@RequestBody @Valid AdminProjectCreateRequest request) {
        Long userId = LoginUserHolder.getCurrentUserId();
        Project project = projectService.createProject(request, userId);
        return ApiResponse.success(project);
    }

    /**
     * Update a project.
     */
    @PutMapping("/update")
    @OperLog(module = "项目管理", operation = "更新项目")
    public ApiResponse<Void> update(@RequestBody @Valid AdminProjectUpdateRequest request) {
        projectService.updateProject(request.id(), request);
        return ApiResponse.success();
    }

    /**
     * Create analysis in project context.
     */
    @PostMapping("/{projectId}/analyses")
    @OperLog(module = "项目管理", operation = "创建分析")
    public ApiResponse<Pipeline> createAnalysis(@PathVariable Long projectId,
                                                @RequestBody @Valid CreateAnalysisRequest request) {
        Long userId = LoginUserHolder.getCurrentUserId();
        Pipeline pipeline = pipelineService.createAnalysis(projectId, request, userId);
        return ApiResponse.success(pipeline);
    }

    /**
     * List analyses (pipelines) for a project.
     */
    @GetMapping("/{projectId}/analyses")
    public ApiResponse<PageResult<Pipeline>> listAnalyses(@PathVariable Long projectId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        PageResult<Pipeline> result = pipelineService.listAnalysesByProject(projectId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * Delete a project.
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "项目管理", operation = "删除项目")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResponse.success();
    }
}
