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

    /** 操作者用户名快照（用户删除后仍可追溯） */
    private String username;

    /** 操作描述，如 "创建用户" */
    private String operation;

    /** 模块，如 "用户管理"、"认证" */
    private String module;

    /** 接口标识，如 "POST /api/admin/users/create" */
    private String method;

    /** 请求参数（TEXT类型，敏感字段已脱敏） */
    private String params;

    /** 执行结果摘要（TEXT类型） */
    private String result;

    /** SUCCESS / FAIL */
    private String status;

    private String ip;
    private LocalDateTime createdAt;

    // 查询辅助字段
    private String startDate;
    private String endDate;
    /** 关键词：模糊匹配 operation / params */
    private String keyword;
}
