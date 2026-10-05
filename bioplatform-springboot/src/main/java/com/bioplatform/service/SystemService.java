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
     */
    SystemConfig getConfig(String key);

    /**
     * 获取配置解密后的原始值（内部调用 LLM 等场景使用）
     */
    String getConfigValue(String key);

    /**
     * 获取当前启用提供商对应的 LLM API Key。
     * 优先取 llm_api_key_&lt;provider&gt;（每提供商独立 key），无则回退全局 llm_api_key。
     */
    String getActiveLlmApiKey();

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
     * 删除配置
     */
    void deleteConfig(String key);

    /**
     * 获取仪表盘统计数据
     *
     * @return 统计数据（用户数、项目数、流水线数、执行数）
     */
    Map<String, Object> getDashboardStats();
}
