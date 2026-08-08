package com.bioplatform.service.impl;

import com.bioplatform.entity.SystemConfig;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.mapper.SystemConfigMapper;
import com.bioplatform.mapper.UserMapper;
import com.bioplatform.service.SystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置服务实现类
 *
 * @author luosg
 */
@Service
public class SystemServiceImpl implements SystemService {

    private static final Logger log = LoggerFactory.getLogger(SystemServiceImpl.class);

    private final SystemConfigMapper systemConfigMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final PipelineMapper pipelineMapper;
    private final PipelineExecutionMapper pipelineExecutionMapper;

    public SystemServiceImpl(SystemConfigMapper systemConfigMapper,
                             UserMapper userMapper,
                             ProjectMapper projectMapper,
                             PipelineMapper pipelineMapper,
                             PipelineExecutionMapper pipelineExecutionMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.userMapper = userMapper;
        this.projectMapper = projectMapper;
        this.pipelineMapper = pipelineMapper;
        this.pipelineExecutionMapper = pipelineExecutionMapper;
    }

    @Override
    public SystemConfig getConfig(String key) {
        return systemConfigMapper.selectByKey(key);
    }

    @Override
    public List<SystemConfig> getAllConfigs() {
        SystemConfig configParam = new SystemConfig();
        return systemConfigMapper.selectAll(configParam);
    }

    @Override
    public void updateConfig(String key, String value) {
        SystemConfig config = systemConfigMapper.selectByKey(key);
        if (config == null) {
            // 如果配置不存在，创建新的
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            systemConfigMapper.insert(config);
        } else {
            // 更新现有配置
            config.setConfigValue(value);
            systemConfigMapper.updateById(config);
        }
        log.info("更新系统配置: key={}, value={}", key, value);
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // 用户总数
        int userCount = userMapper.countAll(null);
        stats.put("userCount", userCount);

        // 项目总数（通过查询所有项目）
        com.bioplatform.entity.Project projectParam = new com.bioplatform.entity.Project();
        List<com.bioplatform.entity.Project> projects = projectMapper.selectAll(projectParam);
        stats.put("projectCount", projects.size());

        // 流水线总数
        com.bioplatform.entity.Pipeline pipelineParam = new com.bioplatform.entity.Pipeline();
        List<com.bioplatform.entity.Pipeline> pipelines = pipelineMapper.selectAll(pipelineParam);
        stats.put("pipelineCount", pipelines.size());

        // 执行记录总数
        com.bioplatform.entity.PipelineExecution executionParam = new com.bioplatform.entity.PipelineExecution();
        List<com.bioplatform.entity.PipelineExecution> executions = pipelineExecutionMapper.selectAll(executionParam);
        stats.put("executionCount", executions.size());

        // 执行状态统计
        List<Map<String, Object>> statusStats = pipelineExecutionMapper.countByStatus();
        stats.put("executionStatusStats", statusStats);

        return stats;
    }
}
