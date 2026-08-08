package com.bioplatform.agent;

/**
 * LLM工具调用记录
 *
 * @param id        工具调用ID
 * @param name      工具名称
 * @param arguments 工具参数（JSON字符串）
 *
 * @author luosg
 */
public record ToolCall(String id, String name, String arguments) {
}
