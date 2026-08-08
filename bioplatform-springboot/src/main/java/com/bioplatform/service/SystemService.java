package com.bioplatform.service;

import com.bioplatform.entity.SystemConfig;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务接口
 *
 * @author luosg
 */
public interface SystemService {

    /**
     * 根据配置键获取配置
     *
     * @param key 配置键
     * @return 配置信息
     */
    SystemConfig getConfig(String key);

    /**
     * 获取所有配置
     *
     * @return 配置列表
     */
    List<SystemConfig> getAllConfigs();

    /**
     * 更新配置
     *
     * @param key   配置键
     * @param value 配置值
     */
    void updateConfig(String key, String value);

    /**
     * 获取仪表盘统计数据
     *
     * @return 统计数据（用户数、项目数、流水线数、执行数）
     */
    Map<String, Object> getDashboardStats();
}
