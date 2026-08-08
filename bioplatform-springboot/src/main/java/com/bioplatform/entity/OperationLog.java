package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志实体类
 *
 * @author luosg
 */
@Data
public class OperationLog {
    private Long id;

    private Long userId;

    private String operation;

    private String method;

    /** 请求参数（TEXT类型） */
    private String params;

    /** 执行结果（TEXT类型） */
    private String result;

    private String ip;
    private LocalDateTime createdAt;
    // 查询辅助字段
    private String startDate;
    private String endDate;
}
