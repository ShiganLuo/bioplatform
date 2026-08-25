package com.bioplatform.service.impl;

import com.bioplatform.common.util.AesEncryptUtil;
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

    /**
     * 获取配置原始值（内部使用，返回解密后的明文）
     */
    @Override
    public String getConfigValue(String key) {
        SystemConfig config = systemConfigMapper.selectByKey(key);
        if (config == null) return null;
        return AesEncryptUtil.decrypt(config.getConfigValue());
    }

    @Override
    public List<SystemConfig> getAllConfigs() {
        SystemConfig configParam = new SystemConfig();
        List<SystemConfig> configs = systemConfigMapper.selectAll(configParam);
        // 敏感字段返回遮蔽值，非敏感字段返回原值
        for (SystemConfig config : configs) {
            if (isSensitiveKey(config.getConfigKey())) {
                config.setConfigValue(AesEncryptUtil.mask(config.getConfigValue()));
            }
        }
        return configs;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("key") || lower.contains("secret") || lower.contains("password") || lower.contains("token");
    }

    @Override
    public void updateConfig(String key, String value) {
        if (value == null) return;
        // 敏感字段：先解密再检查是否为遮蔽值
        if (isSensitiveKey(key)) {
            String realValue = AesEncryptUtil.isEncrypted(value)
                    ? AesEncryptUtil.decrypt(value) : value;
            if (realValue.contains("***")) {
                log.debug("跳过未修改的敏感配置: key={}", key);
                return;
            }
            // 明文加密后存储
            String storedValue = AesEncryptUtil.isEncrypted(value)
                    ? value : AesEncryptUtil.encrypt(value);
            saveOrUpdate(key, storedValue);
        } else {
            saveOrUpdate(key, value);
        }
    }

    private void saveOrUpdate(String key, String storedValue) {
        SystemConfig config = systemConfigMapper.selectByKey(key);
        if (config == null) {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(storedValue);
            systemConfigMapper.insert(config);
        } else {
            config.setConfigValue(storedValue);
            systemConfigMapper.updateById(config);
        }
        log.info("更新系统配置: key={}", key);
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
