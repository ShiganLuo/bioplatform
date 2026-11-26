package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * snakemake benchmark 性能明细(每 job 每 attempt 一行)
 *
 * @author luosg
 */
@Data
public class PipelineBenchmark {
    private Long id;
    private Long executionId;
    private Long pipelineId;
    /** 输出目录一级子目录(工作流名),如 DR */
    private String workflow;
    /** 规则名(config 资源键空间,含 calibrate_resources.py 同构别名映射) */
    private String ruleName;
    /** 样本(路径解析,可空) */
    private String sample;
    /** 基因组(路径解析,可空) */
    private String genome;
    /** 文件内行序(从 1 起) */
    private Integer attempt;
    /** OOM 删失(.OOM 标记,与 calibrate_resources.py 同口径):真实需求 ≥ 观测值 */
    private Boolean censored;
    private Double seconds;
    private Double maxRssMb;
    private Double maxVmsMb;
    private Double maxUssMb;
    private Double maxPssMb;
    private Double ioIn;
    private Double ioOut;
    private Double meanLoad;
    private Double cpuTime;
    /** 相对输出目录的 benchmark 文件路径 */
    private String benchmarkPath;
    /** 冗余自执行记录,便于按版本聚合/隔离校准 */
    private String workflowVersion;
    private LocalDateTime createdAt;

    // ===== 联表填充的非表字段(趋势/明细展示) =====
    /** 所属执行的开始时间(趋势图 X 轴) */
    private LocalDateTime executionStartedAt;
}
