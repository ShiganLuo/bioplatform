package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.DataFile;
import com.bioplatform.service.ChunkUploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分片上传控制器
 *
 * 流程：
 * 1. 前端将大文件切片，调用 /upload-chunk 逐片上传
 * 2. 断点续传时先调 /upload-status 查询已上传的分片
 * 3. 全部上传完成后调用 /merge-chunks 合并
 */
@RestController
@RequestMapping("/api/admin/datafiles")
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    public ChunkUploadController(ChunkUploadService chunkUploadService) {
        this.chunkUploadService = chunkUploadService;
    }

    /**
     * 上传单个分片
     */
    @PostMapping("/upload-chunk")
    @OperLog(module = "数据文件管理", operation = "上传分片")
    public ApiResponse<Map<String, Object>> uploadChunk(
            @RequestParam("chunk") MultipartFile chunk,
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam int totalChunks,
            @RequestParam String fileName) {
        try {
            chunkUploadService.uploadChunk(uploadId, chunkIndex, totalChunks, fileName, chunk.getBytes());
            Map<String, Object> result = new HashMap<>();
            result.put("uploadId", uploadId);
            result.put("chunkIndex", chunkIndex);
            return ApiResponse.success(result);
        } catch (IOException e) {
            return ApiResponse.error(500, "读取分片数据失败");
        }
    }

    /**
     * 查询已上传的分片（断点续传用）
     */
    @GetMapping("/upload-status")
    public ApiResponse<Map<String, Object>> uploadStatus(@RequestParam String uploadId) {
        List<Integer> uploadedChunks = chunkUploadService.getUploadedChunks(uploadId);
        Map<String, Object> result = new HashMap<>();
        result.put("uploadId", uploadId);
        result.put("uploadedChunks", uploadedChunks);
        return ApiResponse.success(result);
    }

    /**
     * 合并所有分片为最终文件
     */
    @PostMapping("/merge-chunks")
    @OperLog(module = "数据文件管理", operation = "合并分片")
    public ApiResponse<DataFile> mergeChunks(
            @RequestParam String uploadId,
            @RequestParam String fileName,
            @RequestParam Long projectId) {
        Long userId = LoginUserHolder.getCurrentUserId();
        try {
            DataFile dataFile = chunkUploadService.mergeChunks(uploadId, fileName, projectId, userId);
            return ApiResponse.success(dataFile);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (RuntimeException e) {
            return ApiResponse.error(500, e.getMessage());
        }
    }
}
