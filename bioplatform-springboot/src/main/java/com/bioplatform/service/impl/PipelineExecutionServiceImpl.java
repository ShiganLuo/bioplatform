package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.service.PipelineExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流水线执行记录服务实现类
 *
 * @author luosg
 */
@Service
public class PipelineExecutionServiceImpl implements PipelineExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionServiceImpl.class);

    private final PipelineExecutionMapper pipelineExecutionMapper;

    public PipelineExecutionServiceImpl(PipelineExecutionMapper pipelineExecutionMapper) {
        this.pipelineExecutionMapper = pipelineExecutionMapper;
    }

    @Override
    public PipelineExecution getExecutionById(Long id) {
        return pipelineExecutionMapper.selectById(id);
    }

    @Override
    public PageResult listByProjectId(Long projectId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByProjectId(projectId, null);
        PageInfo<PipelineExecution> pageInfo = new PageInfo<>(executions);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, executions);
    }

    @Override
    public PageResult listByUserId(Long userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByUserId(userId, null);
        PageInfo<PipelineExecution> pageInfo = new PageInfo<>(executions);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, executions);
    }

    @Override
    public void cancelExecution(Long id) {
        PipelineExecution execution = pipelineExecutionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }

        // 只有PENDING或RUNNING状态的执行才能取消
        if (!"PENDING".equals(execution.getStatus()) && !"RUNNING".equals(execution.getStatus())) {
            throw new IllegalArgumentException("当前状态不允许取消执行");
        }

        execution.setStatus("CANCELLED");
        execution.setFinishedAt(LocalDateTime.now());
        pipelineExecutionMapper.updateById(execution);

        log.info("取消执行成功: executionId={}", id);
    }

    @Override
    public String getExecutionLogs(Long id) {
        PipelineExecution execution = pipelineExecutionMapper.selectById(id);
        if (execution == null) {
            throw new IllegalArgumentException("执行记录不存在");
        }
        // 返回错误日志（如果有），否则返回状态信息
        if (execution.getErrorLog() != null && !execution.getErrorLog().isBlank()) {
            return execution.getErrorLog();
        }
        return "执行状态: " + execution.getStatus() + "\n暂无详细日志";
    }
}
