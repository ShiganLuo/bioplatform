package com.bioplatform.mapper;

import com.bioplatform.entity.SampleMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SampleMetaMapper {

    int insert(SampleMeta sampleMeta);

    SampleMeta selectById(@Param("id") Long id);

    List<SampleMeta> selectByProjectId(@Param("projectId") Long projectId);

    int updateById(SampleMeta sampleMeta);

    int deleteById(@Param("id") Long id);
}
