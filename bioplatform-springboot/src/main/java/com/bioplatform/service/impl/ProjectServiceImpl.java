package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectCreateRequest;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectUpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.front.FrontProjectDTO.FrontProjectListDTO;
import com.bioplatform.entity.Project;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目服务实现类
 *
 * @author luosg
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectServiceImpl.class);

    private final ProjectMapper projectMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public Project createProject(AdminProjectCreateRequest request, Long userId) {
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOwnerId(userId);
        project.setStatus(1); // 默认活跃
        project.setIsPrivate(false); // 默认公开

        projectMapper.insert(project);
        log.info("创建项目成功: projectId={}, name={}", project.getId(), project.getName());
        return project;
    }

    @Override
    public void updateProject(Long id, AdminProjectUpdateRequest request) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }

        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(request.status());

        projectMapper.updateById(project);
        log.info("更新项目成功: projectId={}", id);
    }

    @Override
    public void deleteProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }

        projectMapper.deleteById(id);
        log.info("删除项目成功: projectId={}", id);
    }

    @Override
    public Project getProjectById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public PageResult listUserProjects(Long userId, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Project> projects = projectMapper.selectByOwnerId(userId, null);
        PageInfo<Project> pageInfo = new PageInfo<>(projects);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, projects);
    }

    @Override
    public PageResult listPublicProjects(int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Project> projects = projectMapper.selectPublic();
        PageInfo<Project> pageInfo = new PageInfo<>(projects);

        // 转换为前台DTO
        List<FrontProjectListDTO> dtoList = projects.stream()
                .map(p -> new FrontProjectListDTO(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        null, // ownerNickName需要关联查询，这里简化为null
                        p.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, dtoList);
    }

    @Override
    public PageResult searchProjects(String keyword, int pageNum, int pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        List<Project> projects = projectMapper.searchByName(keyword, null, null);
        PageInfo<Project> pageInfo = new PageInfo<>(projects);

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, projects);
    }
}
