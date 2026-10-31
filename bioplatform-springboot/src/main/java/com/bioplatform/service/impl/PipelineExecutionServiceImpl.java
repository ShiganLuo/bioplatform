package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.service.PipelineExecutionService;
import com.bioplatform.worker.PipelineTaskDispatcher;
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
    private final PipelineTaskDispatcher taskDispatcher;

    public PipelineExecutionServiceImpl(PipelineExecutionMapper pipelineExecutionMapper,
                                        PipelineTaskDispatcher taskDispatcher) {
        this.pipelineExecutionMapper = pipelineExecutionMapper;
        this.taskDispatcher = taskDispatcher;
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

        // 委托 dispatcher:RUNNING 任务会真杀执行端进程(旧版只改 DB 是假取消)
        boolean cancelled = taskDispatcher.cancelExecution(id);
        if (!cancelled) {
            throw new IllegalArgumentException("当前状态不允许取消执行");
        }

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
