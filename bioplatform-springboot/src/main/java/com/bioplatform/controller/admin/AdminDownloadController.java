package com.bioplatform.controller.admin;

import com.bioplatform.common.util.OwnershipUtils;
import com.bioplatform.dto.admin.FileTreeNode;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.DataFile;
import com.bioplatform.entity.Project;
import com.bioplatform.service.DataFileService;
import com.bioplatform.service.DownloadTicketService;
import com.bioplatform.service.ProjectExportService;
import com.bioplatform.service.ProjectService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览器原生下载（一次性票据）
 * <p>
 * 流程：已认证请求 POST /download-ticket 换取短时一次性票据 →
 * 浏览器直接 GET /download?ticket=...（白名单免 header 鉴权，票据即凭证）→
 * 服务端返回 Content-Disposition: attachment，浏览器原生下载器接管
 * （有下载进度、走用户"保存位置"设置）。
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin")
public class AdminDownloadController {

    private static final long MAX_BATCH_DOWNLOAD_BYTES = 200L * 1024 * 1024;

    private final DownloadTicketService ticketService;
    private final DataFileService dataFileService;
    private final ProjectService projectService;
    private final ProjectExportService projectExportService;

    public AdminDownloadController(DownloadTicketService ticketService,
                                   DataFileService dataFileService,
                                   ProjectService projectService,
                                   ProjectExportService projectExportService) {
        this.ticketService = ticketService;
        this.dataFileService = dataFileService;
        this.projectService = projectService;
        this.projectExportService = projectExportService;
    }

    /** type: file / excel / ppt / batch / all */
    public record TicketRequest(String type, Long fileId, Long projectId, List<String> filePaths) {
    }

    /**
     * 签发下载票据（需登录；归属校验在签发时做，下载端点免鉴权后票据即凭证）
     */
    @PostMapping("/download-ticket")
    public ApiResponse<Map<String, String>> issue(@RequestBody TicketRequest request) {
        if (request.type() == null) {
            throw new IllegalArgumentException("缺少下载类型");
        }
        if ("file".equals(request.type())) {
            DataFile dataFile = dataFileService.getFileById(request.fileId());
            if (dataFile == null) {
                throw new IllegalArgumentException("文件不存在");
            }
            OwnershipUtils.checkOwnership(dataFile.getUploadedBy(), "文件");
        } else if (request.projectId() != null) {
            Project project = projectService.getProjectById(request.projectId());
            if (project == null) {
                throw new IllegalArgumentException("项目不存在");
            }
        } else {
            throw new IllegalArgumentException("缺少项目 ID");
        }

        String ticket = ticketService.issue(new DownloadTicketService.Ticket(
                request.type(), request.fileId(), request.projectId(),
                request.filePaths(), System.currentTimeMillis()));
        Map<String, String> result = new HashMap<>();
        result.put("ticket", ticket);
        result.put("url", "/api/admin/download?ticket=" + ticket);
        return ApiResponse.success(result);
    }

    /**
     * 凭票据下载（白名单免鉴权；票据单次消费）
     */
    @GetMapping("/download")
    public void download(@RequestParam String ticket, HttpServletResponse response) throws IOException {
        DownloadTicketService.Ticket t = ticketService.consume(ticket);
        if (t == null) {
            writeJson(response, 410, "下载票据无效或已过期，请重新点击下载");
            return;
        }
        switch (t.type()) {
            case "file" -> downloadFile(t, response);
            case "excel" -> downloadReport(t, response, true);
            case "ppt" -> downloadReport(t, response, false);
            case "batch" -> downloadZip(t.filePaths(), t.projectId(), "_files.zip", response);
            case "all" -> downloadAll(t.projectId(), response);
            default -> writeJson(response, 400, "未知下载类型: " + t.type());
        }
    }

    private void downloadFile(DownloadTicketService.Ticket t, HttpServletResponse response) throws IOException {
        DataFile dataFile = dataFileService.getFileById(t.fileId());
        if (dataFile == null) {
            writeJson(response, 404, "文件不存在");
            return;
        }
        Path filePath = dataFileService.getFilePath(t.fileId());
        String filename = dataFile.getName() != null ? dataFile.getName() : "download";
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", disposition(filename));
        try (InputStream in = Files.newInputStream(filePath)) {
            if (Files.exists(filePath)) {
                response.setContentLengthLong(Files.size(filePath));
            }
            StreamUtils.copy(in, response.getOutputStream());
        } catch (java.io.FileNotFoundException e) {
            writeJson(response, 404, "磁盘上未找到文件");
        }
    }

    private void downloadReport(DownloadTicketService.Ticket t, HttpServletResponse response, boolean excel) throws IOException {
        Project project = projectService.getProjectById(t.projectId());
        String projectName = project != null ? project.getName() : "project";
        if (excel) {
            ByteArrayOutputStream baos = projectExportService.generateExcel(t.projectId());
            downloadResponse(response, baos, projectName + "_report.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            ByteArrayOutputStream baos = projectExportService.generatePpt(t.projectId());
            downloadResponse(response, baos, projectName + "_report.pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
    }

    private void downloadZip(List<String> filePaths, Long projectId, String suffix, HttpServletResponse response) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            writeJson(response, 400, "没有可下载的文件");
            return;
        }
        Project project = projectService.getProjectById(projectId);
        String projectName = project != null ? project.getName() : "project";
        try {
            ByteArrayOutputStream baos = projectExportService.batchDownloadByPaths(filePaths, MAX_BATCH_DOWNLOAD_BYTES);
            downloadResponse(response, baos, projectName + suffix, "application/zip");
        } catch (IllegalArgumentException e) {
            writeJson(response, 400, e.getMessage());
        }
    }

    private void downloadAll(Long projectId, HttpServletResponse response) throws IOException {
        List<FileTreeNode> tree = projectExportService.getFileTree(projectId);
        List<String> allPaths = new ArrayList<>();
        collectFilePaths(tree, allPaths);
        downloadZip(allPaths, projectId, "_all.zip", response);
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
        response.setHeader("Content-Disposition", disposition(filename));
        response.setContentLength(baos.size());
        baos.writeTo(response.getOutputStream());
        response.getOutputStream().flush();
    }

    private String disposition(String filename) {
        String enc = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + enc + "\"; filename*=UTF-8''" + enc;
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
