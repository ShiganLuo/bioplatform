package com.bioplatform.agent;

/**
 * LLM聊天消息记录
 *
 * @param role      消息角色 (system/user/assistant/tool)
 * @param content   消息内容
 * @param toolCallId 工具调用ID（当角色为tool时使用）
 *
 * @author luosg
 */
public record ChatMessage(String role, String content, String toolCallId) {

    /**
     * 创建系统消息
     */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null);
    }

    /**
     * 创建用户消息
     */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null);
    }

    /**
     * 创建助手消息
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null);
    }

    /**
     * 创建工具结果消息
     */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId);
    }
}
