package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineCreateRequest;
import com.bioplatform.dto.admin.AdminPipelineDTO.CreateAnalysisRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.WorkflowTemplate;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.WorkflowTemplateMapper;
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
    private final WorkflowTemplateMapper workflowTemplateMapper;

    public PipelineServiceImpl(PipelineMapper pipelineMapper,
                               PipelineExecutionMapper pipelineExecutionMapper,
                               WorkflowTemplateMapper workflowTemplateMapper) {
        this.pipelineMapper = pipelineMapper;
        this.pipelineExecutionMapper = pipelineExecutionMapper;
        this.workflowTemplateMapper = workflowTemplateMapper;
    }

    @Override
    public Pipeline createPipeline(AdminPipelineCreateRequest request, Long userId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(request.name());
        pipeline.setType(request.type() != null ? request.type() : "pipeline");
        pipeline.setTemplateId(request.templateId());
        pipeline.setProjectId(request.projectId());
        pipeline.setMetaContent(request.metaContent());
        pipeline.setMetaType(request.metaType());
        pipeline.setExtraParams(request.extraParams());
        pipeline.setDescription(request.description());
        pipeline.setCategory(request.category());
        pipeline.setConfigJson(request.configJson());
        pipeline.setDockerImage(request.dockerImage());
        pipeline.setTimeout(request.timeout());
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
        pipeline.setType(request.type());
        pipeline.setTemplateId(request.templateId());
        pipeline.setProjectId(request.projectId());
        pipeline.setMetaContent(request.metaContent());
        pipeline.setMetaType(request.metaType());
        pipeline.setExtraParams(request.extraParams());
        pipeline.setDescription(request.description());
        pipeline.setCategory(request.category());
        pipeline.setConfigJson(request.configJson());
        pipeline.setDockerImage(request.dockerImage());
        pipeline.setTimeout(request.timeout());

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
    public PageResult<Pipeline> listPipelines(String category, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        Pipeline pipelineParam = new Pipeline();
        pipelineParam.setCategory(category);
        List<Pipeline> pipelines = pipelineMapper.selectAll(pipelineParam);
        PageInfo<Pipeline> pageInfo = new PageInfo<>(pipelines);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, pipelines);
    }

    @Override
    public Pipeline createAnalysis(Long projectId, CreateAnalysisRequest request, Long userId) {
        // 查找流程模板
        WorkflowTemplate query = new WorkflowTemplate();
        query.setName(request.workflowTemplateName());
        List<WorkflowTemplate> templates = workflowTemplateMapper.selectAll(query);
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("流程模板不存在: " + request.workflowTemplateName());
        }
        WorkflowTemplate tpl = templates.get(0);

        // 创建 Pipeline
        Pipeline pipeline = new Pipeline();
        pipeline.setName(request.name() != null ? request.name() : tpl.getName() + "-分析");
        pipeline.setType(tpl.getType());
        pipeline.setTemplateId(tpl.getId());
        pipeline.setProjectId(projectId);
        pipeline.setMetaContent(request.metaContent());
        pipeline.setMetaType(request.metaType() != null ? request.metaType() : "text");
        pipeline.setExtraParams(request.extraParams());
        pipeline.setDescription(request.description() != null ? request.description()
                : "从项目创建: " + tpl.getName());
        pipeline.setCategory(tpl.getCategory());
        pipeline.setConfigJson(tpl.getConfigTemplate());
        pipeline.setTimeout(3600);
        pipeline.setOwnerId(userId);

        pipelineMapper.insert(pipeline);
        log.info("创建分析成功: pipelineId={}, name={}, projectId={}", pipeline.getId(), pipeline.getName(), projectId);
        return pipeline;
    }

    @Override
    public PageResult<Pipeline> listAnalysesByProject(Long projectId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        Pipeline param = new Pipeline();
        param.setProjectId(projectId);
        List<Pipeline> list = pipelineMapper.selectAll(param);
        PageInfo<Pipeline> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, list);
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
