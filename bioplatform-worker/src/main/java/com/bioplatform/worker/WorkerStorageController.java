package com.bioplatform.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;

/**
 * Worker 存储 API
 * 供 Gateway 存取文件
 */
@RestController
@RequestMapping("/worker/storage")
public class WorkerStorageController {

    private static final Logger log = LoggerFactory.getLogger(WorkerStorageController.class);

    @Value("${worker.storage.path:./worker-data}")
    private String storagePath;

    /**
     * 上传文件（Gateway → Worker）
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestBody Map<String, Object> params) {
        Long projectId = Long.valueOf(params.get("projectId").toString());
        String fileName = params.get("fileName").toString();
        byte[] data = Base64.getDecoder().decode(params.get("data").toString());

        Path dir = Paths.get(storagePath, String.valueOf(projectId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("创建目录失败", e);
        }

        Path target = dir.resolve(fileName);
        try {
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            String relativePath = projectId + "/" + fileName;
            log.info("文件存储成功: {}", relativePath);
            return Map.of("path", relativePath, "size", data.length);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败", e);
        }
    }

    /**
     * 下载文件（Worker → Gateway）
     */
    @GetMapping("/download")
    public Map<String, Object> download(@RequestParam String path) {
        Path full = Paths.get(storagePath, path);
        if (!Files.exists(full)) {
            return Map.of("data", "", "error", "文件不存在");
        }
        try {
            byte[] data = Files.readAllBytes(full);
            return Map.of("data", Base64.getEncoder().encodeToString(data), "size", data.length);
        } catch (IOException e) {
            return Map.of("data", "", "error", e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestBody Map<String, String> params) {
        String path = params.get("path");
        try {
            Files.deleteIfExists(Paths.get(storagePath, path));
            return Map.of("deleted", true);
        } catch (IOException e) {
            return Map.of("deleted", false, "error", e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     */
    @GetMapping("/exists")
    public Map<String, Object> exists(@RequestParam String path) {
        boolean found = Files.exists(Paths.get(storagePath, path));
        return Map.of("exists", found);
    }
}
