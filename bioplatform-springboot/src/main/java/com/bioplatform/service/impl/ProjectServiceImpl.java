package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectCreateRequest;
import com.bioplatform.dto.admin.AdminProjectDTO.AdminProjectUpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.front.FrontProjectDTO.FrontProjectListDTO;
import com.bioplatform.entity.Project;
import com.bioplatform.entity.User;
import com.bioplatform.mapper.AgentConversationMapper;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.mapper.UserMapper;
import com.bioplatform.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserMapper userMapper;
    private final PipelineExecutionMapper pipelineExecutionMapper;
    private final AgentConversationMapper agentConversationMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper, UserMapper userMapper,
                              PipelineExecutionMapper pipelineExecutionMapper,
                              AgentConversationMapper agentConversationMapper) {
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.pipelineExecutionMapper = pipelineExecutionMapper;
        this.agentConversationMapper = agentConversationMapper;
    }

    @Override
    public Project createProject(AdminProjectCreateRequest request, Long userId) {
        Project project = new Project();
        project.setParentId(request.parentId());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOrganism(request.organism());
        project.setGenomeVersion(request.genomeVersion());
        project.setOwnerId(userId);
        project.setStatus(1); // 默认活跃
        project.setIsPrivate(request.isPrivate() != null ? request.isPrivate() : false);
        if (request.createdAt() != null) {
            project.setCreatedAt(request.createdAt());
        }

        projectMapper.insert(project);
        log.info("创建项目成功: projectId={}, name={}, parentId={}", project.getId(), project.getName(), project.getParentId());
        return project;
    }

    @Override
    public void updateProject(Long id, AdminProjectUpdateRequest request) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }

        project.setParentId(request.parentId());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setOrganism(request.organism());
        project.setGenomeVersion(request.genomeVersion());
        project.setStatus(request.status());
        project.setIsPrivate(request.isPrivate());
        if (request.createdAt() != null) {
            project.setCreatedAt(request.createdAt());
        }

        projectMapper.updateById(project);
        log.info("更新项目成功: projectId={}, parentId={}", id, project.getParentId());
    }

    @Override
    @Transactional
    public void deleteProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在");
        }

        // 有正在执行/排队的任务时不允许删除(否则运行中任务成孤儿)
        int active = pipelineExecutionMapper.countActiveByProjectId(id);
        if (active > 0) {
            throw new IllegalArgumentException("该项目存在 " + active + " 个正在执行的任务，请先取消或等待执行完成后再删除");
        }

        // 连带删除执行记录(fk_pe_project 外键无级联，不先删子行会被 MySQL 拒绝)
        pipelineExecutionMapper.deleteByProjectId(id);
        // 项目下 AI 对话解绑保留历史(fk_ac_project 无级联，project_id 可空)
        agentConversationMapper.unbindProject(id);
        // 先解绑子项目
        projectMapper.unbindChildren(id);
        projectMapper.deleteById(id);
        log.info("删除项目成功: projectId={}", id);
    }

    @Override
    public Project getProjectById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public PageResult listUserProjects(Long userId, int pageNum, int pageSize, String name, String organism) {
        PageHelper.startPage(pageNum, pageSize);
        List<Project> projects = projectMapper.selectByOwnerId(userId, null, name, organism);
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
                .map(p -> {
                    String ownerName = null;
                    if (p.getOwnerId() != null) {
                        User owner = userMapper.selectById(p.getOwnerId());
                        if (owner != null) {
                            // 昵称优先，空则回退用户名（负责人不能显示为空）
                            ownerName = (owner.getNickName() != null && !owner.getNickName().isBlank())
                                    ? owner.getNickName() : owner.getUsername();
                        }
                    }
                    return new FrontProjectListDTO(
                            p.getId(),
                            p.getName(),
                            p.getDescription(),
                            p.getOrganism(),
                            p.getGenomeVersion(),
                            p.getStatus(),
                            ownerName,
                            p.getCreatedAt()
                    );
                })
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
