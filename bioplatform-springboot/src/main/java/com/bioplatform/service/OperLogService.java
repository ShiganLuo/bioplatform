package com.bioplatform.service;

import com.bioplatform.entity.OperationLog;

/**
 * 操作日志Service接口
 *
 * @author luosg
 */
public interface OperLogService {

    /**
     * 保存操作日志
     *
     * @param operationLog 操作日志实体
     */
    void save(OperationLog operationLog);
}
