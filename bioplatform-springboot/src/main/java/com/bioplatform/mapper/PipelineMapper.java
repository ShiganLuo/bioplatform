package com.bioplatform.mapper;

import com.bioplatform.entity.Pipeline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流水线Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface PipelineMapper {

    int insert(Pipeline pipeline);

    Pipeline selectById(@Param("id") Long id);

    List<Pipeline> selectAll(Pipeline pipeline);

    int updateById(Pipeline pipeline);

    int deleteById(@Param("id") Long id);

    List<Pipeline> selectByCategory(@Param("category") String category);

    List<Pipeline> selectByOwnerId(@Param("ownerId") Long ownerId);

    List<Pipeline> searchByName(@Param("name") String name, @Param("category") String category, @Param("ownerId") Long ownerId);
}
