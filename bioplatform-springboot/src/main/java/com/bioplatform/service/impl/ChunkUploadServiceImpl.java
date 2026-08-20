package com.bioplatform.service.impl;

import com.bioplatform.entity.DataFile;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.service.ChunkUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分片上传服务实现
 *
 * 分片存储结构：
 *   {uploadPath}/_chunks/{uploadId}/
 *     0    (第0片)
 *     1    (第1片)
 *     ...
 *     meta.json  (上传元信息：总片数、文件名)
 */
@Service
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private static final Logger log = LoggerFactory.getLogger(ChunkUploadServiceImpl.class);

    private final DataFileMapper dataFileMapper;

    @Value("${bioplatform.upload.path:./uploads}")
    private String uploadPath;

    public ChunkUploadServiceImpl(DataFileMapper dataFileMapper) {
        this.dataFileMapper = dataFileMapper;
    }

    @Override
    public void uploadChunk(String uploadId, int chunkIndex, int totalChunks, String fileName, byte[] chunkData) {
        Path chunkDir = getChunkDir(uploadId);
        try {
            Files.createDirectories(chunkDir);
        } catch (IOException e) {
            throw new RuntimeException("创建分片目录失败", e);
        }

        // 写入分片文件
        Path chunkFile = chunkDir.resolve(String.valueOf(chunkIndex));
        try {
            Files.write(chunkFile, chunkData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("写入分片失败: " + chunkIndex, e);
        }

        // 写入/更新元信息
        Path metaFile = chunkDir.resolve("meta.json");
        String meta = String.format("{\"fileName\":\"%s\",\"totalChunks\":%d}", escapeJson(fileName), totalChunks);
        try {
            Files.writeString(metaFile, meta, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("写入分片元信息失败", e);
        }

        log.debug("分片上传成功: uploadId={}, chunk={}/{}", uploadId, chunkIndex + 1, totalChunks);
    }

    @Override
    public List<Integer> getUploadedChunks(String uploadId) {
        Path chunkDir = getChunkDir(uploadId);
        if (!Files.exists(chunkDir)) {
            return Collections.emptyList();
        }

        try {
            return Files.list(chunkDir)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.matches("\\d+");
                    })
                    .map(p -> Integer.parseInt(p.getFileName().toString()))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("读取已上传分片失败: uploadId={}", uploadId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public DataFile mergeChunks(String uploadId, String fileName, Long projectId, Long userId) {
        Path chunkDir = getChunkDir(uploadId);
        if (!Files.exists(chunkDir)) {
            throw new IllegalArgumentException("分片目录不存在: " + uploadId);
        }

        // 读取元信息获取总片数
        Path metaFile = chunkDir.resolve("meta.json");
        int totalChunks = 0;
        String actualFileName = fileName;
        if (Files.exists(metaFile)) {
            try {
                String meta = Files.readString(metaFile);
                // 简单解析 JSON
                totalChunks = extractInt(meta, "totalChunks");
                String metaName = extractString(meta, "fileName");
                if (metaName != null && !metaName.isEmpty()) {
                    actualFileName = metaName;
                }
            } catch (IOException e) {
                log.warn("读取元信息失败", e);
            }
        }

        if (totalChunks <= 0) {
            // 尝试从分片文件推断
            List<Integer> chunks = getUploadedChunks(uploadId);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("没有找到已上传的分片");
            }
            totalChunks = chunks.get(chunks.size() - 1) + 1;
        }

        // 验证所有分片是否齐全
        List<Integer> uploaded = getUploadedChunks(uploadId);
        if (uploaded.size() < totalChunks) {
            throw new IllegalArgumentException(
                    String.format("分片不完整: 已上传 %d/%d", uploaded.size(), totalChunks));
        }

        // 确定最终存储路径
        String extension = "";
        if (actualFileName.contains(".")) {
            extension = actualFileName.substring(actualFileName.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID() + extension;

        Path projectDir = Paths.get(uploadPath, String.valueOf(projectId));
        try {
            Files.createDirectories(projectDir);
        } catch (IOException e) {
            throw new RuntimeException("创建项目目录失败", e);
        }
        Path targetFile = projectDir.resolve(uniqueFilename);

        // 合并分片
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(targetFile))) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = chunkDir.resolve(String.valueOf(i));
                if (!Files.exists(chunkFile)) {
                    throw new RuntimeException("分片缺失: " + i);
                }
                Files.copy(chunkFile, out);
            }
        } catch (IOException e) {
            throw new RuntimeException("合并分片失败", e);
        }

        // 获取文件大小
        long fileSize;
        try {
            fileSize = Files.size(targetFile);
        } catch (IOException e) {
            fileSize = 0;
        }

        // 获取文件类型
        String fileType = "";
        if (actualFileName.contains(".")) {
            fileType = actualFileName.substring(actualFileName.lastIndexOf(".") + 1);
        }

        // 写入数据库
        DataFile dataFile = new DataFile();
        dataFile.setName(actualFileName);
        dataFile.setPath(targetFile.toString());
        dataFile.setFileType(fileType);
        dataFile.setFileSize(fileSize);
        dataFile.setProjectId(projectId);
        dataFile.setUploadedBy(userId);
        dataFileMapper.insert(dataFile);

        // 清理分片目录
        try {
            deleteDirectory(chunkDir);
        } catch (IOException e) {
            log.warn("清理分片目录失败: {}", uploadId, e);
        }

        log.info("分片合并完成: uploadId={}, fileId={}, name={}, size={}", uploadId, dataFile.getId(), actualFileName, fileSize);
        return dataFile;
    }

    private Path getChunkDir(String uploadId) {
        // 防止路径遍历攻击
        if (uploadId.contains("..") || uploadId.contains("/") || uploadId.contains("\\")) {
            throw new IllegalArgumentException("非法的 uploadId");
        }
        return Paths.get(uploadPath, "_chunks", uploadId);
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int extractInt(String json, String key) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0;
        int start = idx + pattern.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        try { return Integer.parseInt(json.substring(start, end)); } catch (Exception e) { return 0; }
    }

    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
