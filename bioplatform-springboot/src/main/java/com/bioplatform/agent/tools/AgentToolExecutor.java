package com.bioplatform.agent.tools;

import com.bioplatform.agent.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent工具执行器 - 工具注册表与执行器
 * <p>
 * 管理所有可用工具的注册、查询和执行。
 * 工具通过实现 {@link Tool} 接口来注册到此执行器中。
 * </p>
 *
 * @author luosg
 */
@Component
public class AgentToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolExecutor.class);

    /** 工具注册表: toolName -> Tool */
    private final Map<String, Tool> toolRegistry = new LinkedHashMap<>();

    /**
     * 通过Spring自动注入所有Tool实现
     */
    public AgentToolExecutor(List<Tool> tools) {
        for (Tool tool : tools) {
            toolRegistry.put(tool.getName(), tool);
            log.info("注册Agent工具: {} - {}", tool.getName(), tool.getDescription());
        }
        log.info("共注册{}个Agent工具", toolRegistry.size());
    }

    /**
     * 执行指定工具
     *
     * @param toolName 工具名称
     * @param args     工具参数
     * @return 执行结果（JSON字符串）
     */
    public String executeTool(String toolName, Map<String, String> args) {
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            log.warn("未找到工具: {}", toolName);
            return "{\"error\": \"工具不存在: " + toolName + "\"}";
        }

        try {
            log.info("执行工具: {}, 参数: {}", toolName, args);
            String result = tool.execute(args);
            log.info("工具执行完成: {}", toolName);
            return result;
        } catch (Exception e) {
            log.error("工具执行异常: {}", toolName, e);
            return "{\"error\": \"工具执行失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取所有注册工具的定义列表（用于LLM工具调用）
     *
     * @return 工具定义列表
     */
    public List<ToolDefinition> getAllToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : toolRegistry.values()) {
            definitions.add(new ToolDefinition(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getParameters()
            ));
        }
        return definitions;
    }

    /**
     * 获取指定名称的工具
     *
     * @param toolName 工具名称
     * @return Tool实例，不存在则返回null
     */
    public Tool getTool(String toolName) {
        return toolRegistry.get(toolName);
    }

    /**
     * 检查工具是否已注册
     *
     * @param toolName 工具名称
     * @return 是否已注册
     */
    public boolean hasTool(String toolName) {
        return toolRegistry.containsKey(toolName);
    }

    /**
     * 获取所有已注册工具的名称列表
     *
     * @return 工具名称列表
     */
    public List<String> listToolNames() {
        return new ArrayList<>(toolRegistry.keySet());
    }
}
