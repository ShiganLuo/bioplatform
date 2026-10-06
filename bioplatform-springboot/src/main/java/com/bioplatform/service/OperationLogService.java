package com.bioplatform.service;

import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.OperationLog;

/**
 * 操作日志服务接口
 *
 * @author luosg
 */
public interface OperationLogService {

    /**
     * 保存操作日志
     *
     * @param log 操作日志
     */
    void saveLog(OperationLog log);

    /**
     * 记录一条审计日志（供登录/登出/注册等无 @OperLog 切面的场景显式调用）。
     * 内部兜底：异常只记日志不抛出，绝不影响业务主流程。
     *
     * @param module    模块，如 "认证"
     * @param operation 操作描述，如 "登录成功"
     * @param method    接口标识，如 "POST /api/admin/auth/login"
     * @param userId    用户ID（可能未知，如用户不存在的登录失败）
     * @param username  用户名
     * @param ip        客户端IP
     * @param params    参数摘要（调用方需自行脱敏）
     * @param status    SUCCESS / FAIL
     * @param errorMsg  失败原因（可空）
     */
    void record(String module, String operation, String method,
                Long userId, String username, String ip,
                String params, String status, String errorMsg);

    /**
     * 分页查询操作日志
     *
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param filter  筛选条件（username/module/status/operation/keyword/startDate/endDate/userId/ip）
     * @return 分页结果
     */
    PageResult listLogs(int pageNum, int pageSize, OperationLog filter);
}
