package com.bioplatform.storage;

import com.bioplatform.entity.DataFile;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.worker.WorkerClient;
import com.bioplatform.worker.WorkerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 分布式存储策略（内网隔离模式）
 * 文件存在内网 Worker 服务器上
 * Gateway 只存元数据，需要读写时通过 HTTP 中转
 *
 * 存储路径格式: {workerId}:{projectId}/{filename}
 * 例如: worker-abc:3/sample.fastq
 *
 * @author luosg
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "bioplatform.storage.type", havingValue = "worker")
public class WorkerStorageStrategy implements StorageStrategy {

    private static final Logger log = LoggerFactory.getLogger(WorkerStorageStrategy.class);

    private final WorkerRegistry workerRegistry;
    private final WorkerClient workerClient;
    private final DataFileMapper dataFileMapper;

    public WorkerStorageStrategy(WorkerRegistry workerRegistry, WorkerClient workerClient,
                                  DataFileMapper dataFileMapper) {
        this.workerRegistry = workerRegistry;
        this.workerClient = workerClient;
        this.dataFileMapper = dataFileMapper;
    }

    @Override
    public String getType() {
        return "worker";
    }

    @Override
    public String store(MultipartFile file, Long projectId, String fileName) {
        // 选择一个 Worker 存储
        WorkerRegistry.WorkerInfo worker = selectStorageWorker();
        if (worker == null) {
            throw new RuntimeException("没有可用的存储节点");
        }

        try {
            String remotePath = workerClient.uploadFile(worker.getUrl(), projectId, fileName, file.getBytes());
            log.info("分布式存储写入: worker={}, path={}", worker.getId(), remotePath);
            return worker.getId() + ":" + remotePath;
        } catch (Exception e) {
            throw new RuntimeException("分布式存储写入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String storeBytes(byte[] data, Long projectId, String fileName) {
        WorkerRegistry.WorkerInfo worker = selectStorageWorker();
        if (worker == null) {
            throw new RuntimeException("没有可用的存储节点");
        }

        try {
            String remotePath = workerClient.uploadFile(worker.getUrl(), projectId, fileName, data);
            log.info("分布式存储写入(bytes): worker={}, path={}", worker.getId(), remotePath);
            return worker.getId() + ":" + remotePath;
        } catch (Exception e) {
            throw new RuntimeException("分布式存储写入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Path resolve(String storagePath) {
        // 分布式模式下，先下载到本地临时文件再返回路径
        String[] parts = parseStoragePath(storagePath);
        String workerId = parts[0];
        String remotePath = parts[1];

        WorkerRegistry.WorkerInfo worker = workerRegistry.getById(workerId);
        if (worker == null) {
            throw new RuntimeException("存储节点不存在: " + workerId);
        }

        try {
            byte[] data = workerClient.downloadFile(worker.getUrl(), remotePath);
            // 写入临时文件
            Path tmp = java.nio.file.Files.createTempFile("bioplatform-", ".tmp");
            java.nio.file.Files.write(tmp, data);
            tmp.toFile().deleteOnExit();
            return tmp;
        } catch (Exception e) {
            throw new RuntimeException("从 Worker 下载文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storagePath) {
        String[] parts = parseStoragePath(storagePath);
        String workerId = parts[0];
        String remotePath = parts[1];

        WorkerRegistry.WorkerInfo worker = workerRegistry.getById(workerId);
        if (worker != null) {
            try {
                workerClient.deleteFile(worker.getUrl(), remotePath);
            } catch (Exception e) {
                log.warn("分布式存储删除失败: {}", storagePath, e);
            }
        }
    }

    @Override
    public boolean exists(String storagePath) {
        String[] parts = parseStoragePath(storagePath);
        String workerId = parts[0];
        String remotePath = parts[1];

        WorkerRegistry.WorkerInfo worker = workerRegistry.getById(workerId);
        if (worker == null) return false;
        return workerClient.fileExists(worker.getUrl(), remotePath);
    }

    @Override
    public long size(String storagePath) {
        // 简化实现
        return exists(storagePath) ? 0 : -1;
    }

    /**
     * 解析存储路径: workerId:remotePath
     */
    private String[] parseStoragePath(String storagePath) {
        int idx = storagePath.indexOf(':');
        if (idx <= 0) {
            throw new IllegalArgumentException("无效的分布式存储路径: " + storagePath);
        }
        return new String[]{ storagePath.substring(0, idx), storagePath.substring(idx + 1) };
    }

    /**
     * 选择存储 Worker（优先选空闲内存最多的）
     */
    private WorkerRegistry.WorkerInfo selectStorageWorker() {
        List<WorkerRegistry.WorkerInfo> workers = workerRegistry.getHealthyWorkers();
        return workers.stream()
                .max(java.util.Comparator.comparingLong(WorkerRegistry.WorkerInfo::getFreeMemoryGB))
                .orElse(null);
    }
}
