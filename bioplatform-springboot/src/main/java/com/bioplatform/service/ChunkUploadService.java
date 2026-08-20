package com.bioplatform.service;

import com.bioplatform.entity.DataFile;

import java.util.List;

/**
 * 分片上传服务接口
 */
public interface ChunkUploadService {

    /**
     * 上传单个分片
     *
     * @param uploadId  上传任务ID（前端生成，用于标识同一次上传）
     * @param chunkIndex 分片索引（从0开始）
     * @param totalChunks 总分片数
     * @param fileName  原始文件名
     * @param chunkData 分片数据
     */
    void uploadChunk(String uploadId, int chunkIndex, int totalChunks, String fileName, byte[] chunkData);

    /**
     * 查询已上传的分片列表（用于断点续传）
     *
     * @param uploadId 上传任务ID
     * @return 已上传的分片索引列表
     */
    List<Integer> getUploadedChunks(String uploadId);

    /**
     * 合并所有分片为最终文件，并登记到数据库
     *
     * @param uploadId  上传任务ID
     * @param fileName  原始文件名
     * @param projectId 项目ID
     * @param userId    操作者ID
     * @return 文件记录
     */
    DataFile mergeChunks(String uploadId, String fileName, Long projectId, Long userId);
}
