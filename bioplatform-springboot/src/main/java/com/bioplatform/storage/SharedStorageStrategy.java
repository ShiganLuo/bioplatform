package com.bioplatform.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 共享存储策略
 * 所有服务器（Gateway + Workers）挂载同一个 NFS/NAS 目录
 * 文件直接读写共享路径，无需网络传输
 *
 * @author luosg
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "bioplatform.storage.type", havingValue = "shared", matchIfMissing = true)
public class SharedStorageStrategy implements StorageStrategy {

    private static final Logger log = LoggerFactory.getLogger(SharedStorageStrategy.class);

    @Value("${bioplatform.storage.shared-path:/data/shared/bioplatform}")
    private String sharedPath;

    @Override
    public String getType() {
        return "shared";
    }

    @Override
    public String store(MultipartFile file, Long projectId, String fileName) {
        String uniqueName = UUID.randomUUID().toString() + "_" + fileName;
        Path targetDir = getProjectDir(projectId);
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(uniqueName);
            file.transferTo(target.toFile());
            log.info("共享存储写入: {}", target);
            return relativePath(projectId, uniqueName);
        } catch (IOException e) {
            throw new RuntimeException("共享存储写入失败", e);
        }
    }

    @Override
    public String storeBytes(byte[] data, Long projectId, String fileName) {
        String uniqueName = UUID.randomUUID().toString() + "_" + fileName;
        Path targetDir = getProjectDir(projectId);
        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(uniqueName);
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("共享存储写入(bytes): {}", target);
            return relativePath(projectId, uniqueName);
        } catch (IOException e) {
            throw new RuntimeException("共享存储写入失败", e);
        }
    }

    @Override
    public Path resolve(String storagePath) {
        if (isAbsolute(storagePath)) {
            Path absolute = Paths.get(storagePath);
            if (!Files.exists(absolute)) {
                throw new RuntimeException("文件不存在: " + absolute);
            }
            return absolute;
        }
        Path full = Paths.get(sharedPath, storagePath);
        if (!Files.exists(full)) {
            throw new RuntimeException("文件不存在: " + full);
        }
        return full;
    }

    @Override
    public void delete(String storagePath) {
        try {
            // 直接按路径删除，不走 resolve（resolve 对不存在的文件会抛异常，
            // 导致"磁盘已丢失的文件"无法从库中清理）
            Path target = isAbsolute(storagePath)
                    ? Paths.get(storagePath)
                    : Paths.get(sharedPath, storagePath);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("共享存储删除失败: {}", storagePath, e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        if (isAbsolute(storagePath)) {
            return Files.exists(Paths.get(storagePath));
        }
        return Files.exists(Paths.get(sharedPath, storagePath));
    }

    @Override
    public long size(String storagePath) {
        try {
            if (isAbsolute(storagePath)) {
                Path absolute = Paths.get(storagePath);
                return Files.exists(absolute) ? Files.size(absolute) : -1;
            }
            Path p = Paths.get(sharedPath, storagePath);
            return Files.exists(p) ? Files.size(p) : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    private Path getProjectDir(Long projectId) {
        return Paths.get(sharedPath, String.valueOf(projectId));
    }

    /**
     * 存储路径格式: {projectId}/{uuid_filename}
     * resolve 时拼接 sharedPath 前缀
     */
    private String relativePath(Long projectId, String fileName) {
        return projectId + "/" + fileName;
    }

    /** import-local 登记的是服务器绝对路径，解析时必须原样使用，不得拼接 sharedPath */
    private boolean isAbsolute(String storagePath) {
        return storagePath != null && Paths.get(storagePath).isAbsolute();
    }
}
