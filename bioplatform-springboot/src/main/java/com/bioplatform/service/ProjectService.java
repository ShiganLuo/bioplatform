package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectCreateRequest;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectUpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.Project;

/**
 * 项目服务接口
 *
 * @author luosg
 */
public interface ProjectService {

    /**
     * 创建项目
     *
     * @param request 创建项目请求
     * @param userId  创建者ID
     * @return 项目信息
     */
    Project createProject(AdminProjectCreateRequest request, Long userId);

    /**
     * 更新项目
     *
     * @param id      项目ID
     * @param request 更新项目请求
     */
    void updateProject(Long id, AdminProjectUpdateRequest request);

    /**
     * 删除项目
     *
     * @param id 项目ID
     */
    void deleteProject(Long id);

    /**
     * 根据ID获取项目
     *
     * @param id 项目ID
     * @return 项目信息
     */
    Project getProjectById(Long id);

    /**
     * 分页查询用户的项目列表
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult listUserProjects(Long userId, int pageNum, int pageSize);

    /**
     * 分页查询公开项目列表（前台页面）
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult listPublicProjects(int pageNum, int pageSize);

    /**
     * 搜索项目
     *
     * @param keyword  搜索关键词
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult searchProjects(String keyword, int pageNum, int pageSize);
}
