package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线执行记录实体类
 *
 * @author luosg
 */
@Data
public class PipelineExecution {
    private Long id;

    private Long pipelineId;

    private Long projectId;

    private Long userId;

    /** 状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String status;

    /** 输入参数JSON（TEXT类型） */
    private String inputParams;

    private String outputPath;

    /** 错误日志（TEXT类型） */
    private String errorLog;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
