package com.bioplatform.agent.tools;

import java.util.Map;

/**
 * Agent工具接口
 * 所有工具都需要实现此接口，以便在Agent系统中注册和调用
 *
 * @author luosg
 */
public interface Tool {

    /**
     * 获取工具名称
     *
     * @return 工具名称（唯一标识）
     */
    String getName();

    /**
     * 获取工具描述
     *
     * @return 工具描述（用于LLM理解工具功能）
     */
    String getDescription();

    /**
     * 获取工具参数定义（JSON Schema格式）
     *
     * @return 参数定义Map
     */
    Map<String, Object> getParameters();

    /**
     * 执行工具
     *
     * @param args 参数键值对
     * @return 执行结果（JSON字符串）
     */
    String execute(Map<String, String> args);
}
