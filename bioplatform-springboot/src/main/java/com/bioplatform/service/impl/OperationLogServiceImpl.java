package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.OperationLog;
import com.bioplatform.mapper.OperationLogMapper;
import com.bioplatform.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志服务实现类
 *
 * @author luosg
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogServiceImpl.class);

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    public void saveLog(OperationLog logEntry) {
        operationLogMapper.insert(logEntry);
        log.debug("保存操作日志: operation={}, userId={}", logEntry.getOperation(), logEntry.getUserId());
    }

    @Override
    public PageResult listLogs(int pageNum, int pageSize, Long userId, String operation) {
        PageHelper.startPage(pageNum, pageSize);

        OperationLog logParam = new OperationLog();
        logParam.setUserId(userId);
        logParam.setOperation(operation);

        List<OperationLog> logs = operationLogMapper.selectWithFilter(logParam);
        PageInfo<OperationLog> pageInfo = new PageInfo<>(logs);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, logs);
    }
}
