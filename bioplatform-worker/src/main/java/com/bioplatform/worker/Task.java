package com.bioplatform.worker;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 执行任务实体
 */
@Data
public class Task {
    private String id;
    private Long executionId;
    private Long pipelineId;
    private String command;
    private String inputParams;
    /** 超时秒数(<=0 表示不限) */
    private Integer timeout;
    /** PENDING/RUNNING/COMPLETED/FAILED/CANCELLED */
    private String status;
    private String output;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
