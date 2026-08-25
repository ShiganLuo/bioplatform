package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineCreateRequest;
import com.bioplatform.dto.admin.AdminPipelineDTO.CreateAnalysisRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;

/**
 * 流水线服务接口
 *
 * @author luosg
 */
public interface PipelineService {

    Pipeline createPipeline(AdminPipelineCreateRequest request, Long userId);

    void updatePipeline(Long id, AdminPipelineCreateRequest request);

    void deletePipeline(Long id);

    Pipeline getPipelineById(Long id);

    PageResult<Pipeline> listPipelines(String category, int pageNum, int pageSize);

    /**
     * 从项目上下文创建分析
     */
    Pipeline createAnalysis(Long projectId, CreateAnalysisRequest request, Long userId);

    /**
     * 列出项目下的分析（Pipeline）
     */
    PageResult<Pipeline> listAnalysesByProject(Long projectId, int pageNum, int pageSize);

    PipelineExecution executePipeline(Long pipelineId, Long projectId, String inputParams, Long userId);
}
