package com.bioplatform.service;

import com.bioplatform.dto.common.PageResult;
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
}
