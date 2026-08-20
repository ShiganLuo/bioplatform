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

    List<DataFile> selectByProjectId(@Param("projectId") Long projectId, @Param("fileType") String fileType);

    List<DataFile> searchByName(@Param("name") String name, @Param("projectId") Long projectId, @Param("fileType") String fileType);

    List<DataFile> selectByFileType(@Param("fileType") String fileType, @Param("projectId") Long projectId);

    long sumFileSizeByUser(@Param("userId") Long userId);

    long sumTotalFileSize();
}
