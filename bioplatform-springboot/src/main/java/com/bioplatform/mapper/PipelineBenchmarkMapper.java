package com.bioplatform.mapper;

import com.bioplatform.entity.PipelineBenchmark;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * snakemake benchmark 性能明细 Mapper
 *
 * @author luosg
 */
@Mapper
public interface PipelineBenchmarkMapper {

    /** 幂等插入(uk_pb_exec_path_attempt 冲突忽略) */
    int insertIgnore(PipelineBenchmark benchmark);

    List<PipelineBenchmark> selectByExecutionId(@Param("executionId") Long executionId);

    /** 明细分页(方案二):过滤 pipeline/工作流/规则/执行/版本 */
    List<PipelineBenchmark> selectList(@Param("pipelineId") Long pipelineId,
                                       @Param("workflow") String workflow,
                                       @Param("ruleName") String ruleName,
                                       @Param("executionId") Long executionId,
                                       @Param("version") String version);

    /** 趋势(方案二):联执行开始时间,按执行时序 */
    List<PipelineBenchmark> selectTrend(@Param("pipelineId") Long pipelineId,
                                        @Param("ruleName") String ruleName);

    /** 按版本统计数据点数 */
    List<java.util.Map<String, Object>> countPointsByVersion(@Param("pipelineId") Long pipelineId);

    /** 校准导出:指定流水线/工作流/版本,且执行版本来源为 git 的行 */
    List<PipelineBenchmark> selectCalibrationRows(@Param("pipelineId") Long pipelineId,
                                                  @Param("workflow") String workflow,
                                                  @Param("version") String version);
}
