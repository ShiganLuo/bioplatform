package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminPipelineDTO.AdminPipelineCreateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;

/**
 * 流水线服务接口
 *
 * @author luosg
 */
public interface PipelineService {

    /**
     * 创建流水线
     *
     * @param request 创建流水线请求
     * @param userId  创建者ID
     * @return 流水线信息
     */
    Pipeline createPipeline(AdminPipelineCreateRequest request, Long userId);

    /**
     * 更新流水线
     *
     * @param id      流水线ID
     * @param request 更新流水线请求
     */
    void updatePipeline(Long id, AdminPipelineCreateRequest request);

    /**
     * 删除流水线
     *
     * @param id 流水线ID
     */
    void deletePipeline(Long id);

    /**
     * 根据ID获取流水线
     *
     * @param id 流水线ID
     * @return 流水线信息
     */
    Pipeline getPipelineById(Long id);

    /**
     * 分页查询流水线列表
     *
     * @param category 分类
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult listPipelines(String category, int pageNum, int pageSize);

    /**
     * 执行流水线
     *
     * @param pipelineId  流水线ID
     * @param projectId   项目ID
     * @param inputParams 输入参数JSON
     * @param userId      执行用户ID
     * @return 执行记录
     */
    PipelineExecution executePipeline(Long pipelineId, Long projectId, String inputParams, Long userId);
}
