package com.bioplatform.controller.front;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Project;
import com.bioplatform.service.ProjectService;
import org.springframework.web.bind.annotation.*;

/**
 * Front-end project controller for public viewing.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front/projects")
public class FrontProjectController {

    private final ProjectService projectService;

    public FrontProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * List public projects.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult result = projectService.listPublicProjects(page, size);
        return ApiResponse.success(result);
    }

    /**
     * Get project detail by id.
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
     * Search projects by keyword.
     */
    @GetMapping("/search")
    public ApiResponse<PageResult> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult result = projectService.searchProjects(keyword, page, size);
        return ApiResponse.success(result);
    }
}
