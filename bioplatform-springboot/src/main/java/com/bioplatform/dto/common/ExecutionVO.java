package com.bioplatform.dto.common;

/**
 * 执行记录 VO(字段对齐前端:名称/进度/结果等实体没有的展示字段在此组装)。
 *
 * @author luosg
 */
public record ExecutionVO(
        Long id,
        Long pipelineId,
        String pipelineName,
        Long projectId,
        String projectName,
        Long userId,
        String status,
        String params,
        String result,
        String errorMessage,
        Integer progress,
        String progressText,
        String executor,
        String outputPath,
        String taskId,
        String startTime,
        String endTime,
        String createdAt) {
}
