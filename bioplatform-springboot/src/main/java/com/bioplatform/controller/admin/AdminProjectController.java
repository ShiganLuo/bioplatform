package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.common.util.OwnershipUtils;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectCreateRequest;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectUpdateRequest;
import com.bioplatform.dto.admin.AdminPipelineDTO.CreateAnalysisRequest;
import com.bioplatform.dto.admin.FileTreeNode;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.Project;
import com.bioplatform.service.ProjectExportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final ProjectExportService projectExportService;
    private final com.bioplatform.mapper.ProjectMapper projectMapper;

    /** 全部下载大小限制：200MB */
    private static final long MAX_BATCH_DOWNLOAD_BYTES = 200L * 1024 * 1024;

    public AdminProjectController(com.bioplatform.service.ProjectService projectService,
                                  com.bioplatform.service.PipelineService pipelineService,
                                  ProjectExportService projectExportService,
                                  com.bioplatform.mapper.ProjectMapper projectMapper) {
        this.projectService = projectService;
        this.pipelineService = pipelineService;
        this.projectExportService = projectExportService;
        this.projectMapper = projectMapper;
    }

    /**
     * Paginated project list (with parent name).
     * 普通用户可见自己的 + 公开项目，管理员看所有
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String organism) {
        com.github.pagehelper.PageHelper.startPage(page, size);
        java.util.List<Map<String, Object>> list;
        if (OwnershipUtils.isAdmin()) {
            list = projectMapper.selectAdminList(name, organism);
        } else {
            Long userId = OwnershipUtils.getCurrentUserId();
            list = projectMapper.selectAdminListAccessible(userId, name, organism);
        }
        com.github.pagehelper.PageInfo<Map<String, Object>> pageInfo = new com.github.pagehelper.PageInfo<>(list);
        return ApiResponse.success(PageResult.of(pageInfo.getTotal(), page, size, list));
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
        // 读取可见性：管理员/本人/公开项目
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
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
        Project existing = projectService.getProjectById(request.id());
        if (existing == null) return ApiResponse.error(404, "项目不存在");
        OwnershipUtils.checkProjectOwnership(existing.getOwnerId());
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
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            return ApiResponse.error(404, "项目不存在");
        }
        // 写操作：仅本人/管理员可在项目中创建分析
        OwnershipUtils.checkProjectOwnership(project.getOwnerId());
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
        Project project = projectService.getProjectById(projectId);
        if (project == null) {
            return ApiResponse.error(404, "项目不存在");
        }
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
        PageResult<Pipeline> result = pipelineService.listAnalysesByProject(projectId, page, size);
        return ApiResponse.success(result);
    }

    /**
     * Get distinct organisms from all projects.
     */
    @GetMapping("/organisms")
    public ApiResponse<List<String>> getOrganisms() {
        return ApiResponse.success(projectMapper.selectDistinctOrganisms());
    }

    /**
     * Get distinct genome versions from all projects.
     */
    @GetMapping("/genome-versions")
    public ApiResponse<List<String>> getGenomeVersions() {
        return ApiResponse.success(projectMapper.selectDistinctGenomeVersions());
    }

    /**
     * Get parent project candidates (top-level projects for dropdown).
     */
    @GetMapping("/parents")
    public ApiResponse<List<Project>> getParentCandidates() {
        // 管理员看全部；普通用户只看本人+公开项目（父项目下拉）
        Long userId = OwnershipUtils.isAdmin() ? null : OwnershipUtils.getCurrentUserId();
        return ApiResponse.success(projectMapper.selectParentCandidates(userId));
    }

    /**
     * Delete a project.
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "项目管理", operation = "删除项目")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Project existing = projectService.getProjectById(id);
        if (existing == null) return ApiResponse.error(404, "项目不存在");
        OwnershipUtils.checkProjectOwnership(existing.getOwnerId());
        projectService.deleteProject(id);
        return ApiResponse.success();
    }

    /**
     * Unbind a project from its parent.
     */
    @PutMapping("/{id}/unbind-parent")
    @OperLog(module = "项目管理", operation = "解除父项目绑定")
    public ApiResponse<Void> unbindParent(@PathVariable Long id) {
        Project existing = projectService.getProjectById(id);
        if (existing == null) return ApiResponse.error(404, "项目不存在");
        OwnershipUtils.checkProjectOwnership(existing.getOwnerId());
        projectMapper.clearParent(id);
        return ApiResponse.success();
    }

    // ========== 导出接口 ==========

    /**
     * 导出项目 Excel 报表
     */
    @GetMapping("/{id}/export/excel")
    public void exportExcel(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Project project = projectService.getProjectById(id);
        if (project == null) {
            response.setStatus(404);
            return;
        }
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
        ByteArrayOutputStream baos = projectExportService.generateExcel(id);
        String filename = project.getName() + "_report.xlsx";
        downloadResponse(response, baos, filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * 导出项目 PPT 报告
     */
    @GetMapping("/{id}/export/ppt")
    public void exportPpt(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Project project = projectService.getProjectById(id);
        if (project == null) {
            response.setStatus(404);
            return;
        }
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
        ByteArrayOutputStream baos = projectExportService.generatePpt(id);
        String filename = project.getName() + "_report.pptx";
        downloadResponse(response, baos, filename,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    /**
     * 获取项目文件树
     */
    @GetMapping("/{id}/files/tree")
    public ApiResponse<List<FileTreeNode>> getFileTree(@PathVariable Long id) {
        Project project = projectService.getProjectById(id);
        if (project == null) {
            return ApiResponse.error(404, "项目不存在");
        }
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
        return ApiResponse.success(projectExportService.getFileTree(id));
    }

    /**
     * 批量打包下载文件（按文件路径）
     */
    @PostMapping("/{id}/files/batch-download")
    public void batchDownload(@PathVariable Long id,
                              @RequestBody List<String> filePaths,
                              HttpServletResponse response) throws IOException {
        Project project = projectService.getProjectById(id);
        if (project == null) {
            response.setStatus(404);
            return;
        }
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
        ByteArrayOutputStream baos = projectExportService.batchDownloadByPaths(id, filePaths, MAX_BATCH_DOWNLOAD_BYTES);
        String filename = project.getName() + "_files.zip";
        downloadResponse(response, baos, filename, "application/zip");
    }

    /**
     * 全部下载（zip）- 扫描所有执行输出目录
     */
    @GetMapping("/{id}/export/download-all")
    public void downloadAll(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Project project = projectService.getProjectById(id);
        if (project == null) {
            response.setStatus(404);
            return;
        }
        OwnershipUtils.checkProjectReadAccess(project.getOwnerId(), project.getIsPrivate());
        List<FileTreeNode> tree = projectExportService.getFileTree(id);
        List<String> allPaths = new ArrayList<>();
        collectFilePaths(tree, allPaths);
        if (allPaths.isEmpty()) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":400,\"message\":\"没有可下载的文件\"}");
            return;
        }
        ByteArrayOutputStream baos = projectExportService.batchDownloadByPaths(id, allPaths, MAX_BATCH_DOWNLOAD_BYTES);
        String filename = project.getName() + "_all.zip";
        downloadResponse(response, baos, filename, "application/zip");
    }

    private void collectFilePaths(List<FileTreeNode> nodes, List<String> paths) {
        for (FileTreeNode node : nodes) {
            if (!node.isDirectory() && node.getFilePath() != null) {
                paths.add(node.getFilePath());
            }
            if (node.getChildren() != null) {
                collectFilePaths(node.getChildren(), paths);
            }
        }
    }

    private void downloadResponse(HttpServletResponse response, ByteArrayOutputStream baos,
                                  String filename, String contentType) throws IOException {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8") + "\"");
        response.setContentLength(baos.size());
        baos.writeTo(response.getOutputStream());
        response.getOutputStream().flush();
    }
}
