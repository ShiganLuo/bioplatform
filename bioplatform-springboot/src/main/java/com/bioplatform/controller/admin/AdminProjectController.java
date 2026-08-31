package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
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
     * Paginated project list.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String organism) {
        Long userId = LoginUserHolder.getCurrentUserId();
        PageResult<Project> result = projectService.listUserProjects(userId, page, size, name, organism);
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
     * Delete a project.
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "项目管理", operation = "删除项目")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ApiResponse.success();
    }

    // ========== 导出接口 ==========

    /**
     * 导出项目 Excel 报表
     */
    @GetMapping("/{id}/export/excel")
    public void exportExcel(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ByteArrayOutputStream baos = projectExportService.generateExcel(id);
        Project project = projectService.getProjectById(id);
        String filename = (project != null ? project.getName() : "project") + "_report.xlsx";
        downloadResponse(response, baos, filename,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * 导出项目 PPT 报告
     */
    @GetMapping("/{id}/export/ppt")
    public void exportPpt(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ByteArrayOutputStream baos = projectExportService.generatePpt(id);
        Project project = projectService.getProjectById(id);
        String filename = (project != null ? project.getName() : "project") + "_report.pptx";
        downloadResponse(response, baos, filename,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    }

    /**
     * 获取项目文件树
     */
    @GetMapping("/{id}/files/tree")
    public ApiResponse<List<FileTreeNode>> getFileTree(@PathVariable Long id) {
        return ApiResponse.success(projectExportService.getFileTree(id));
    }

    /**
     * 批量打包下载文件（按文件路径）
     */
    @PostMapping("/{id}/files/batch-download")
    public void batchDownload(@PathVariable Long id,
                              @RequestBody List<String> filePaths,
                              HttpServletResponse response) throws IOException {
        ByteArrayOutputStream baos = projectExportService.batchDownloadByPaths(filePaths, MAX_BATCH_DOWNLOAD_BYTES);
        Project project = projectService.getProjectById(id);
        String filename = (project != null ? project.getName() : "project") + "_files.zip";
        downloadResponse(response, baos, filename, "application/zip");
    }

    /**
     * 全部下载（zip）- 扫描所有执行输出目录
     */
    @GetMapping("/{id}/export/download-all")
    public void downloadAll(@PathVariable Long id, HttpServletResponse response) throws IOException {
        List<FileTreeNode> tree = projectExportService.getFileTree(id);
        List<String> allPaths = new ArrayList<>();
        collectFilePaths(tree, allPaths);
        if (allPaths.isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":400,\"message\":\"没有可下载的文件\"}");
            return;
        }
        ByteArrayOutputStream baos = projectExportService.batchDownloadByPaths(allPaths, MAX_BATCH_DOWNLOAD_BYTES);
        Project project = projectService.getProjectById(id);
        String filename = (project != null ? project.getName() : "project") + "_all.zip";
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
