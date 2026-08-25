package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.datafile.StorageInfo;
import com.bioplatform.entity.DataFile;
import com.bioplatform.service.DataFileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin data file management controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/datafiles")
public class AdminDataFileController {

    private final DataFileService dataFileService;

    public AdminDataFileController(DataFileService dataFileService) {
        this.dataFileService = dataFileService;
    }

    /**
     * 检查存储空间和用户配额
     *
     * @param pendingSize 待上传文件总大小（字节），可选
     */
    @GetMapping("/storage-check")
    public ApiResponse<StorageInfo> storageCheck(
            @RequestParam(defaultValue = "0") long pendingSize) {
        Long userId = LoginUserHolder.getCurrentUserId();
        StorageInfo info = dataFileService.checkStorage(userId, pendingSize);
        return ApiResponse.success(info);
    }

    /**
     * List files by project.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult result;
        if (projectId != null) {
            result = dataFileService.listByProjectId(projectId, page, size);
        } else {
            result = dataFileService.listAllFiles(page, size);
        }
        return ApiResponse.success(result);
    }

    /**
     * Upload a file.
     */
    @PostMapping("/upload")
    @OperLog(module = "数据文件管理", operation = "上传文件")
    public ApiResponse<DataFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam(required = false) String organism,
            @RequestParam(required = false) String genomeVersion) {
        Long userId = LoginUserHolder.getCurrentUserId();

        // 上传前校验存储空间
        StorageInfo storage = dataFileService.checkStorage(userId, file.getSize());
        if (!storage.isCanUpload()) {
            return ApiResponse.error(400, storage.getReason());
        }

        DataFile dataFile = dataFileService.uploadFile(file, projectId, organism, genomeVersion, userId);
        return ApiResponse.success(dataFile);
    }

    /**
     * 批量上传文件（支持文件夹上传，保留目录结构）
     */
    @PostMapping("/batch-upload")
    @OperLog(module = "数据文件管理", operation = "批量上传文件")
    public ApiResponse<List<DataFile>> batchUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("relativePaths") String[] relativePaths,
            @RequestParam Long projectId) {
        if (files.length != relativePaths.length) {
            return ApiResponse.error(400, "文件数量与路径数量不匹配");
        }

        Long userId = LoginUserHolder.getCurrentUserId();

        // 计算待上传总大小并校验
        long totalSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        StorageInfo storage = dataFileService.checkStorage(userId, totalSize);
        if (!storage.isCanUpload()) {
            return ApiResponse.error(400, storage.getReason());
        }

        List<DataFile> result = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            DataFile dataFile = dataFileService.uploadFileWithRelativePath(
                    files[i], projectId, relativePaths[i], userId);
            result.add(dataFile);
        }
        return ApiResponse.success(result);
    }

    /**
     * 导入服务器本地目录中的文件（不上传，仅登记元数据）
     */
    @PostMapping("/import-local")
    @OperLog(module = "数据文件管理", operation = "导入本地文件")
    public ApiResponse<Map<String, Object>> importLocal(
            @RequestParam String dirPath,
            @RequestParam Long projectId) {
        Long userId = LoginUserHolder.getCurrentUserId();
        try {
            int count = dataFileService.importLocalFiles(dirPath, projectId, userId);
            Map<String, Object> result = new HashMap<>();
            result.put("count", count);
            result.put("dirPath", dirPath);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 获取 rsync 传输信息（供用户在本地终端执行）
     */
    @GetMapping("/rsync-info")
    public ApiResponse<Map<String, String>> rsyncInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("host", "localhost");
        info.put("port", "22");
        info.put("uploadPath", "/home/luosg/uploads/bioplatform");
        info.put("example",
                "rsync -avz --progress ./your_data/ user@localhost:/home/luosg/uploads/bioplatform/{projectId}/\n" +
                "rsync -avz --progress ./your_file.bam user@localhost:/home/luosg/uploads/bioplatform/{projectId}/");
        return ApiResponse.success(info);
    }

    /**
     * Delete a file.
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "数据文件管理", operation = "删除文件")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataFileService.deleteFile(id);
        return ApiResponse.success();
    }

    /**
     * Get file info by id.
     */
    @GetMapping("/{id}")
    public ApiResponse<DataFile> getById(@PathVariable Long id) {
        DataFile dataFile = dataFileService.getFileById(id);
        if (dataFile == null) {
            return ApiResponse.error(404, "文件不存在");
        }
        return ApiResponse.success(dataFile);
    }

    /**
     * Download a file.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DataFile dataFile = dataFileService.getFileById(id);
        if (dataFile == null) {
            return ResponseEntity.notFound().build();
        }
        Path filePath = dataFileService.getFilePath(id);
        Resource resource = new FileSystemResource(filePath.toFile());
        String filename = dataFile.getName() != null ? dataFile.getName() : "download";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
