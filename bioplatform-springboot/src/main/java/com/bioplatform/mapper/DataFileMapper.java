package com.bioplatform.mapper;

import com.bioplatform.entity.DataFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据文件Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface DataFileMapper {

    int insert(DataFile dataFile);

    DataFile selectById(@Param("id") Long id);

    List<DataFile> selectAll(DataFile dataFile);

    int updateById(DataFile dataFile);

    int deleteById(@Param("id") Long id);

    List<DataFile> selectByProjectId(@Param("projectId") Long projectId, @Param("fileType") String fileType, @Param("name") String name, @Param("fileStatus") Integer fileStatus);

    List<DataFile> searchByName(@Param("name") String name, @Param("projectId") Long projectId, @Param("fileType") String fileType);

    List<DataFile> selectByFileType(@Param("fileType") String fileType, @Param("projectId") Long projectId);

    DataFile selectByHashAndProject(@Param("contentHash") String contentHash, @Param("projectId") Long projectId);

    /** 批量写入文件-项目归属 */
    int insertFileProject(@Param("fileId") Long fileId, @Param("projectIds") List<Long> projectIds);

    /** 清空文件的全部项目归属 */
    int deleteFileProject(@Param("fileId") Long fileId);

    long sumFileSizeByUser(@Param("userId") Long userId);

    long sumTotalFileSize();

    /** 仅更新文件状态（0=丢失 1=在库），不触碰其他列 */
    int updateFileStatus(@Param("id") Long id, @Param("fileStatus") Integer fileStatus);

    /** path 精确匹配（样本 meta 文件引用关联用，LIMIT 上限防止病态数据） */
    List<DataFile> searchByPathExact(@Param("path") String path);

    /** path 后缀匹配（rawPath 为相对/部分路径时，LIKE CONCAT('%', path)） */
    List<DataFile> searchByPathSuffix(@Param("path") String path);
}
