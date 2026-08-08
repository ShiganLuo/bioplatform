package com.bioplatform.agent;

import java.util.Map;

/**
 * 工具定义记录，用于向LLM描述可用工具
 *
 * @param name        工具名称
 * @param description 工具描述
 * @param parameters  工具参数定义（JSON Schema格式）
 *
 * @author luosg
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameters) {
}
