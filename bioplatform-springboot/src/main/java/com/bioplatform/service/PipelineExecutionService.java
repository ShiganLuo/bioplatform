package com.bioplatform.service;

import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.PipelineExecution;

/**
 * 流水线执行记录服务接口
 *
 * @author luosg
 */
public interface PipelineExecutionService {

    /**
     * 根据ID获取执行记录
     *
     * @param id 执行记录ID
     * @return 执行记录
     */
    PipelineExecution getExecutionById(Long id);

    /**
     * 分页查询项目的执行记录
     *
     * @param projectId 项目ID
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 分页结果
     */
    PageResult listByProjectId(Long projectId, int pageNum, int pageSize);

    /**
     * 分页查询用户的执行记录
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult listByUserId(Long userId, int pageNum, int pageSize);

    /**
     * 取消执行
     *
     * @param id 执行记录ID
     */
    void cancelExecution(Long id);

    /**
     * 获取执行日志
     *
     * @param id 执行记录ID
     * @return 日志内容
     */
    String getExecutionLogs(Long id);
}
