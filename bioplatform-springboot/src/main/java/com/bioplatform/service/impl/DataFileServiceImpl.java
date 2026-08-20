package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.datafile.StorageInfo;
import com.bioplatform.entity.DataFile;
import com.bioplatform.entity.User;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.mapper.UserMapper;
import com.bioplatform.service.DataFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 数据文件服务实现类
 *
 * @author luosg
 */
@Service
public class DataFileServiceImpl implements DataFileService {

    private static final Logger log = LoggerFactory.getLogger(DataFileServiceImpl.class);

    private final DataFileMapper dataFileMapper;
    private final UserMapper userMapper;

    @Value("${bioplatform.upload.path:./uploads}")
    private String uploadPath;

    public DataFileServiceImpl(DataFileMapper dataFileMapper, UserMapper userMapper) {
        this.dataFileMapper = dataFileMapper;
        this.userMapper = userMapper;
    }

    @Override
    public DataFile uploadFile(MultipartFile file, Long projectId, String organism, String genomeVersion, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // 创建上传目录
        Path uploadDir = Paths.get(uploadPath, String.valueOf(projectId));
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("创建上传目录失败", e);
        }

        // 保存文件
        Path filePath = uploadDir.resolve(uniqueFilename);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }

        // 获取文件类型
        String fileType = originalFilename;
        if (fileType != null && fileType.contains(".")) {
            fileType = fileType.substring(fileType.lastIndexOf(".") + 1);
        }

        // 保存文件记录
        DataFile dataFile = new DataFile();
        dataFile.setName(originalFilename);
        dataFile.setPath(filePath.toString());
        dataFile.setFileType(fileType);
        dataFile.setFileSize(file.getSize());
        dataFile.setOrganism(organism);
        dataFile.setGenomeVersion(genomeVersion);
        dataFile.setProjectId(projectId);
        dataFile.setUploadedBy(userId);

        dataFileMapper.insert(dataFile);

        log.info("文件上传成功: fileId={}, name={}", dataFile.getId(), originalFilename);
        return dataFile;
    }

    @Override
    public DataFile uploadFileWithRelativePath(MultipartFile file, Long projectId, String relativePath, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();

        // 解析相对路径：保留目录结构，去掉文件名
        String subDir = "";
        String fileName = originalFilename;
        if (relativePath != null && relativePath.contains("/")) {
            subDir = relativePath.substring(0, relativePath.lastIndexOf("/"));
            fileName = relativePath.substring(relativePath.lastIndexOf("/") + 1);
        }

        // 创建上传目录（保留文件夹结构）
        Path uploadDir = Paths.get(uploadPath, String.valueOf(projectId), subDir);
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("创建上传目录失败", e);
        }

        // 保存文件（用原始文件名，不加UUID，保留文件夹语义）
        Path filePath = uploadDir.resolve(fileName);
        try {
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败", e);
        }

        // 获取文件类型
        String fileType = "";
        if (fileName != null && fileName.contains(".")) {
            fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
        }

        // 保存文件记录
        DataFile dataFile = new DataFile();
        dataFile.setName(relativePath != null ? relativePath : originalFilename);
        dataFile.setPath(filePath.toString());
        dataFile.setFileType(fileType);
        dataFile.setFileSize(file.getSize());
        dataFile.setProjectId(projectId);
        dataFile.setUploadedBy(userId);

        dataFileMapper.insert(dataFile);

        log.info("文件上传成功: fileId={}, relativePath={}", dataFile.getId(), relativePath);
        return dataFile;
    }

    @Override
    public PageResult listByProjectId(Long projectId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<DataFile> files = dataFileMapper.selectByProjectId(projectId, null);
        PageInfo<DataFile> pageInfo = new PageInfo<>(files);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, files);
    }

    @Override
    public void deleteFile(Long id) {
        DataFile dataFile = dataFileMapper.selectById(id);
        if (dataFile == null) {
            throw new IllegalArgumentException("文件不存在");
        }

        // 删除物理文件
        if (dataFile.getPath() != null) {
            try {
                Path filePath = Paths.get(dataFile.getPath());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.warn("删除物理文件失败: {}", dataFile.getPath(), e);
            }
        }

        // 删除数据库记录
        dataFileMapper.deleteById(id);

        log.info("删除文件成功: fileId={}, name={}", id, dataFile.getName());
    }

    @Override
    public PageResult listAllFiles(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<DataFile> files = dataFileMapper.selectAll(new DataFile());
        PageInfo<DataFile> pageInfo = new PageInfo<>(files);
        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, files);
    }

    @Override
    public DataFile getFileById(Long id) {
        return dataFileMapper.selectById(id);
    }

    @Override
    public java.nio.file.Path getFilePath(Long id) {
        DataFile dataFile = dataFileMapper.selectById(id);
        if (dataFile == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        if (dataFile.getPath() == null) {
            throw new IllegalArgumentException("文件路径不存在");
        }
        java.nio.file.Path filePath = java.nio.file.Paths.get(dataFile.getPath());
        if (!java.nio.file.Files.exists(filePath)) {
            throw new IllegalArgumentException("物理文件不存在");
        }
        return filePath;
    }

    @Override
    public StorageInfo checkStorage(Long userId, long pendingSize) {
        StorageInfo info = new StorageInfo();
        info.setPendingSize(pendingSize);

        // 1. 磁盘空间
        File uploadDir = new File(uploadPath);
        File partition = uploadDir.exists() ? uploadDir : new File("/");
        info.setDiskTotal(partition.getTotalSpace());
        info.setDiskFree(partition.getUsableSpace());
        info.setDiskUsed(partition.getTotalSpace() - partition.getFreeSpace());

        // 2. 用户配额
        User user = userMapper.selectById(userId);
        long quota = (user != null && user.getUploadQuota() != null) ? user.getUploadQuota() : 10737418240L;
        info.setUserQuota(quota);

        // 3. 用户已用空间
        long userUsed = dataFileMapper.sumFileSizeByUser(userId);
        info.setUserUsed(userUsed);
        info.setUserRemaining(quota - userUsed);

        // 4. 判断是否可上传
        boolean canUpload = true;
        String reason = null;

        if (partition.getUsableSpace() < pendingSize) {
            canUpload = false;
            reason = "服务器磁盘空间不足";
        } else if (userUsed + pendingSize > quota) {
            canUpload = false;
            reason = "超出个人上传配额（已用 " + formatBytes(userUsed) + " / 配额 " + formatBytes(quota) + "）";
        }

        info.setCanUpload(canUpload);
        info.setReason(reason);
        return info;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.2f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
    }

    @Override
    public int importLocalFiles(String dirPath, Long projectId, Long userId) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("目录不存在: " + dirPath);
        }

        List<DataFile> imported = new ArrayList<>();
        scanAndRegister(dir, dir, projectId, userId, imported);

        for (DataFile df : imported) {
            dataFileMapper.insert(df);
        }

        log.info("导入本地文件完成: dir={}, count={}", dirPath, imported.size());
        return imported.size();
    }

    /**
     * 递归扫描目录，将文件登记为 DataFile
     *
     * @param root      根目录（用于计算相对路径）
     * @param current   当前扫描目录
     * @param projectId 项目ID
     * @param userId    操作者ID
     * @param result    收集导入结果
     */
    private void scanAndRegister(File root, File current, Long projectId, Long userId, List<DataFile> result) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanAndRegister(root, file, projectId, userId, result);
            } else if (file.isFile()) {
                // 计算相对于根目录的路径
                String relativePath = root.toPath().relativize(file.toPath()).toString();
                // 统一用 / 分隔符
                relativePath = relativePath.replace('\\', '/');

                String fileName = file.getName();
                String fileType = "";
                if (fileName.contains(".")) {
                    fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
                }

                DataFile dataFile = new DataFile();
                dataFile.setName(relativePath);
                dataFile.setPath(file.getAbsolutePath());
                dataFile.setFileType(fileType);
                dataFile.setFileSize(file.length());
                dataFile.setProjectId(projectId);
                dataFile.setUploadedBy(userId);

                result.add(dataFile);
            }
        }
    }
}
