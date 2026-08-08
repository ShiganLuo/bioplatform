package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineCreateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.service.PipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流水线服务实现类
 *
 * @author luosg
 */
@Service
public class PipelineServiceImpl implements PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineServiceImpl.class);

    private final PipelineMapper pipelineMapper;
    private final PipelineExecutionMapper pipelineExecutionMapper;

    public PipelineServiceImpl(PipelineMapper pipelineMapper,
                               PipelineExecutionMapper pipelineExecutionMapper) {
        this.pipelineMapper = pipelineMapper;
        this.pipelineExecutionMapper = pipelineExecutionMapper;
    }

    @Override
    public Pipeline createPipeline(AdminPipelineCreateRequest request, Long userId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(request.name());
        pipeline.setDescription(request.description());
        pipeline.setOwnerId(userId);

        pipelineMapper.insert(pipeline);
        log.info("创建流水线成功: pipelineId={}, name={}", pipeline.getId(), pipeline.getName());
        return pipeline;
    }

    @Override
    public void updatePipeline(Long id, AdminPipelineCreateRequest request) {
        Pipeline pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new IllegalArgumentException("流水线不存在");
        }

        pipeline.setName(request.name());
        pipeline.setDescription(request.description());

        pipelineMapper.updateById(pipeline);
        log.info("更新流水线成功: pipelineId={}", id);
    }

    @Override
    public void deletePipeline(Long id) {
        Pipeline pipeline = pipelineMapper.selectById(id);
        if (pipeline == null) {
            throw new IllegalArgumentException("流水线不存在");
        }

        pipelineMapper.deleteById(id);
        log.info("删除流水线成功: pipelineId={}", id);
    }

    @Override
    public Pipeline getPipelineById(Long id) {
        return pipelineMapper.selectById(id);
    }

    @Override
    public PageResult listPipelines(String category, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        Pipeline pipelineParam = new Pipeline();
        pipelineParam.setCategory(category);
        List<Pipeline> pipelines = pipelineMapper.selectAll(pipelineParam);
        PageInfo<Pipeline> pageInfo = new PageInfo<>(pipelines);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, pipelines);
    }

    @Override
    public PipelineExecution executePipeline(Long pipelineId, Long projectId, String inputParams, Long userId) {
        // 验证流水线是否存在
        Pipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            throw new IllegalArgumentException("流水线不存在");
        }

        // 创建执行记录
        PipelineExecution execution = new PipelineExecution();
        execution.setPipelineId(pipelineId);
        execution.setProjectId(projectId);
        execution.setUserId(userId);
        execution.setStatus("PENDING");
        execution.setInputParams(inputParams);
        execution.setStartedAt(LocalDateTime.now());

        pipelineExecutionMapper.insert(execution);

        log.info("流水线执行创建成功: executionId={}, pipelineId={}", execution.getId(), pipelineId);

        // TODO: 这里应该异步执行流水线任务，实际项目中需要集成Docker或K8s执行器
        // 目前简化为直接更新状态为RUNNING
        execution.setStatus("RUNNING");
        pipelineExecutionMapper.updateById(execution);

        return execution;
    }
}
