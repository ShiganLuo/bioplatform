package com.bioplatform.service;

import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.datafile.StorageInfo;
import com.bioplatform.entity.DataFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据文件服务接口
 *
 * @author luosg
 */
public interface DataFileService {

    /**
     * 上传文件
     *
     * @param file          文件
     * @param projectId     项目ID
     * @param organism      物种
     * @param genomeVersion 基因组版本
     * @param userId        上传者ID
     * @return 数据文件信息
     */
    DataFile uploadFile(MultipartFile file, Long projectId, String organism, String genomeVersion, Long userId);

    /**
     * 上传文件（保留目录结构）
     *
     * @param file          文件
     * @param projectId     项目ID
     * @param relativePath  相对路径（文件夹上传时保留目录结构）
     * @param userId        上传者ID
     * @return 数据文件信息
     */
    DataFile uploadFileWithRelativePath(MultipartFile file, Long projectId, String relativePath, Long userId);

    /**
     * 分页查询项目的文件列表
     *
     * @param projectId 项目ID
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 分页结果
     */
    PageResult listByProjectId(Long projectId, int pageNum, int pageSize);

    /**
     * 删除文件
     *
     * @param id 文件ID
     */
    void deleteFile(Long id);

    /**
     * 分页查询所有文件
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult listAllFiles(int pageNum, int pageSize);

    /**
     * 根据ID获取文件信息
     *
     * @param id 文件ID
     * @return 文件信息
     */
    DataFile getFileById(Long id);

    /**
     * 获取文件下载路径
     *
     * @param id 文件ID
     * @return 文件物理路径
     */
    java.nio.file.Path getFilePath(Long id);

    /**
     * 检查存储空间和用户配额
     *
     * @param userId      用户ID
     * @param pendingSize 待上传文件总大小（字节）
     * @return 存储信息
     */
    StorageInfo checkStorage(Long userId, long pendingSize);

    /**
     * 导入服务器本地目录中的文件到数据库（不复制文件，仅登记元数据）
     *
     * @param dirPath   服务器上的目录路径
     * @param projectId 项目ID
     * @param userId    操作者ID
     * @return 导入的文件数量
     */
    int importLocalFiles(String dirPath, Long projectId, Long userId);
}
