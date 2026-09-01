package com.bioplatform.agent;

import java.util.List;

/**
 * LLM聊天消息记录
 *
 * @param role      消息角色 (system/user/assistant/tool)
 * @param content   消息内容
 * @param toolCallId 工具调用ID（当角色为tool时使用）
 * @param toolCalls  工具调用列表（当角色为assistant且发起工具调用时使用）
 *
 * @author luosg
 */
public record ChatMessage(String role, String content, String toolCallId, List<ToolCallReference> toolCalls) {

    /**
     * 工具调用引用（嵌入在助手消息中）
     */
    public record ToolCallReference(String id, String name, String arguments) {}

    /**
     * 创建系统消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    /**
     * 创建用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    /**
     * 创建助手消息（纯文本，无工具调用）
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    /**
     * 创建助手消息（带工具调用）
     */
    public static ChatMessage assistantWithToolCalls(String content, List<ToolCallReference> toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }

    /**
     * 创建工具结果消息
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
