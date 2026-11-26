package com.bioplatform.mapper;

import com.bioplatform.entity.PipelineExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 流水线执行记录Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface PipelineExecutionMapper {

    int insert(PipelineExecution execution);

    PipelineExecution selectById(@Param("id") Long id);

    List<PipelineExecution> selectAll(PipelineExecution execution);

    int updateById(PipelineExecution execution);

    int deleteById(@Param("id") Long id);

    int deleteByPipelineId(@Param("pipelineId") Long pipelineId);

    int deleteByProjectId(@Param("projectId") Long projectId);

    /** 正在执行/排队中的任务数(PENDING/RUNNING) */
    int countActiveByPipelineId(@Param("pipelineId") Long pipelineId);

    int countActiveByProjectId(@Param("projectId") Long projectId);

    List<PipelineExecution> selectByProjectId(@Param("projectId") Long projectId, @Param("status") String status);

    List<PipelineExecution> selectByUserId(@Param("userId") Long userId, @Param("status") String status);

    List<PipelineExecution> selectByPipelineId(@Param("pipelineId") Long pipelineId, @Param("status") String status);

    List<Map<String, Object>> countByStatus();
}
