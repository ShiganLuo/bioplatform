package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.DataFile;
import com.bioplatform.service.DataFileService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     * List files by project.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult> list(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult result = dataFileService.listByProjectId(projectId, pageNum, pageSize);
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
        DataFile dataFile = dataFileService.uploadFile(file, projectId, organism, genomeVersion, userId);
        return ApiResponse.success(dataFile);
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
}
