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

    /** 终态执行(回填/补偿扫描用):按开始时间升序 */
    List<PipelineExecution> selectTerminalByPipelineId(@Param("pipelineId") Long pipelineId);

    /** 近期终态执行(补偿收割用):finished_at >= since */
    List<PipelineExecution> selectTerminalSince(@Param("since") java.time.LocalDateTime since);

    /** 版本分布(版本列表端点):各版本执行数 */
    List<java.util.Map<String, Object>> countByVersion(@Param("pipelineId") Long pipelineId);

    List<Map<String, Object>> countByStatus();
}
