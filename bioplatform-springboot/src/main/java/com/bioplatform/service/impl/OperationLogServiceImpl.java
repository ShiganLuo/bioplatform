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

import java.time.LocalDateTime;
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
    public void record(String module, String operation, String method,
                       Long userId, String username, String ip,
                       String params, String status, String errorMsg) {
        try {
            OperationLog entry = new OperationLog();
            entry.setModule(module);
            entry.setOperation(operation);
            entry.setMethod(method);
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setIp(ip);
            entry.setParams(params);
            entry.setStatus(status);
            entry.setResult(errorMsg != null ? errorMsg : "成功");
            entry.setCreatedAt(LocalDateTime.now());
            operationLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计写入失败只记应用日志，绝不影响业务主流程
            log.error("保存审计日志失败: module={}, operation={}, error={}",
                    module, operation, e.getMessage());
        }
    }

    @Override
    public PageResult listLogs(int pageNum, int pageSize, OperationLog filter) {
        PageHelper.startPage(pageNum, pageSize);
        List<OperationLog> logs = operationLogMapper.selectWithFilter(filter != null ? filter : new OperationLog());
        PageInfo<OperationLog> pageInfo = new PageInfo<>(logs);
        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, logs);
    }
}
