package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.DataFile;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.service.DataFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${bioplatform.upload.path:./uploads}")
    private String uploadPath;

    public DataFileServiceImpl(DataFileMapper dataFileMapper) {
        this.dataFileMapper = dataFileMapper;
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
}
