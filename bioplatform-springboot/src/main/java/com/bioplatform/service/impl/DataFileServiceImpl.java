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
import com.bioplatform.storage.StorageStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据文件服务实现类
 * 通过 StorageStrategy 屏蔽共享存储和分布式存储的差异
 *
 * @author luosg
 */
@Service
public class DataFileServiceImpl implements DataFileService {

    private static final Logger log = LoggerFactory.getLogger(DataFileServiceImpl.class);

    private final DataFileMapper dataFileMapper;
    private final UserMapper userMapper;
    private final StorageStrategy storage;

    public DataFileServiceImpl(DataFileMapper dataFileMapper, UserMapper userMapper,
                                StorageStrategy storage) {
        this.dataFileMapper = dataFileMapper;
        this.userMapper = userMapper;
        this.storage = storage;
    }

    @Override
    public DataFile uploadFile(MultipartFile file, Long projectId, String organism, String genomeVersion, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();

        // 通过存储策略保存文件
        String storagePath = storage.store(file, projectId, originalFilename);

        // 获取文件类型
        String fileType = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileType = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        // 保存文件记录（只存元数据）
        DataFile dataFile = new DataFile();
        dataFile.setName(originalFilename);
        dataFile.setPath(storagePath);  // 存储策略返回的路径
        dataFile.setFileType(fileType);
        dataFile.setFileSize(file.getSize());
        dataFile.setOrganism(organism);
        dataFile.setGenomeVersion(genomeVersion);
        dataFile.setProjectId(projectId);
        dataFile.setUploadedBy(userId);

        dataFileMapper.insert(dataFile);
        log.info("文件上传成功: fileId={}, name={}, storage={}", dataFile.getId(), originalFilename, storage.getType());
        return dataFile;
    }

    @Override
    public DataFile uploadFileWithRelativePath(MultipartFile file, Long projectId, String relativePath, Long userId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String fileName = relativePath;
        if (relativePath != null && relativePath.contains("/")) {
            fileName = relativePath.substring(relativePath.lastIndexOf("/") + 1);
        }

        String storagePath = storage.store(file, projectId, fileName != null ? fileName : file.getOriginalFilename());

        String fileType = "";
        if (fileName != null && fileName.contains(".")) {
            fileType = fileName.substring(fileName.lastIndexOf(".") + 1);
        }

        DataFile dataFile = new DataFile();
        dataFile.setName(relativePath != null ? relativePath : file.getOriginalFilename());
        dataFile.setPath(storagePath);
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

        // 通过存储策略删除物理文件
        if (dataFile.getPath() != null) {
            storage.delete(dataFile.getPath());
        }

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
    public Path getFilePath(Long id) {
        DataFile dataFile = dataFileMapper.selectById(id);
        if (dataFile == null) {
            throw new IllegalArgumentException("文件不存在");
        }
        if (dataFile.getPath() == null) {
            throw new IllegalArgumentException("文件路径不存在");
        }
        // 通过存储策略解析路径（共享存储返回本地路径，分布式存储会先下载到临时文件）
        return storage.resolve(dataFile.getPath());
    }

    @Override
    public StorageInfo checkStorage(Long userId, long pendingSize) {
        StorageInfo info = new StorageInfo();
        info.setPendingSize(pendingSize);

        // 磁盘空间（仅共享存储模式有意义）
        File uploadDir = new File("/tmp");
        info.setDiskTotal(uploadDir.getTotalSpace());
        info.setDiskFree(uploadDir.getUsableSpace());
        info.setDiskUsed(uploadDir.getTotalSpace() - uploadDir.getFreeSpace());

        // 用户配额
        User user = userMapper.selectById(userId);
        long quota = (user != null && user.getUploadQuota() != null) ? user.getUploadQuota() : 10737418240L;
        info.setUserQuota(quota);

        // 用户已用空间
        long userUsed = dataFileMapper.sumFileSizeByUser(userId);
        info.setUserUsed(userUsed);
        info.setUserRemaining(quota - userUsed);

        // 判断是否可上传
        boolean canUpload = true;
        String reason = null;
        if (userUsed + pendingSize > quota) {
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
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
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

    private void scanAndRegister(File root, File current, Long projectId, Long userId, List<DataFile> result) {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanAndRegister(root, file, projectId, userId, result);
            } else if (file.isFile()) {
                String relativePath = root.toPath().relativize(file.toPath()).toString();
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
