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
     * 分页查询操作日志
     *
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @param userId    用户ID（可选）
     * @param operation 操作类型（可选）
     * @return 分页结果
     */
    PageResult listLogs(int pageNum, int pageSize, Long userId, String operation);
}
