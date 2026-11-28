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

    /** 执行该任务的 Worker ID */
    private String workerId;

    /** 执行该任务的 Worker URL */
    private String workerUrl;

    /** Worker 端任务 ID（落库，backend 重启后可继续对账） */
    private String taskId;

    /** Omics 仓库 git commit(派发时采集);执行级事实,一执行一版本 */
    private String workflowVersion;

    /** 版本来源: git=派发采集 / inferred=历史推断 / unknown */
    private String versionSource;

    /** 执行输入快照 JSON({meta,design,config,command},派发时采集) */
    private String inputSnapshot;

    /** 执行器类型：local / remote */
    private String executor;

    /** 最近一次状态心跳时间（调度线程每次查询后更新） */
    private LocalDateTime heartbeatAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
