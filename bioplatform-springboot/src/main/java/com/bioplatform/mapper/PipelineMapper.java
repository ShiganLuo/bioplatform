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

    /** 列表版本回填:每条流水线最近一次执行(按 id)的版本(2026-11-26) */
    List<java.util.Map<String, Object>> selectLatestExecVersions(@Param("ids") List<Long> ids);

    /** 列表版本回填:模板导入时的 Omics 版本 */
    List<java.util.Map<String, Object>> selectTemplateVersions(@Param("ids") List<Long> ids);

    /** 引用该模板的流程数(删除模板前的引用检查) */
    int countByTemplateId(@Param("templateId") Long templateId);

    List<Pipeline> selectByCategory(@Param("category") String category);

    List<Pipeline> selectByOwnerId(@Param("ownerId") Long ownerId);

    List<Pipeline> searchByName(@Param("name") String name, @Param("category") String category, @Param("ownerId") Long ownerId);
}
