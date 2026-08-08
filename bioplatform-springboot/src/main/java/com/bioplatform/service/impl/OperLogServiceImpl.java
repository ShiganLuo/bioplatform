package com.bioplatform.service.impl;

import com.bioplatform.entity.OperationLog;
import com.bioplatform.mapper.OperationLogMapper;
import com.bioplatform.service.OperLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 操作日志Service实现
 *
 * @author luosg
 */
@Service
public class OperLogServiceImpl implements OperLogService {

    private static final Logger log = LoggerFactory.getLogger(OperLogServiceImpl.class);

    private final OperationLogMapper operationLogMapper;

    public OperLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void save(OperationLog operationLog) {
        try {
            operationLogMapper.insert(operationLog);
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage(), e);
        }
    }
}
