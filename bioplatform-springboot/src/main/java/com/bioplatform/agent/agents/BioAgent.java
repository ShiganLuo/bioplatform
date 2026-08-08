package com.bioplatform.agent.agents;

import com.bioplatform.agent.LLMClient;
import com.bioplatform.agent.ToolDefinition;

import java.util.List;
import java.util.Map;

/**
 * 生物信息学Agent抽象基类
 * <p>
 * 所有专业Agent都需要继承此类，实现handle方法。
 * 提供通用的名称、系统提示词和LLM客户端。
 * </p>
 *
 * @author luosg
 */
public abstract class BioAgent {

    /** Agent名称（唯一标识） */
    protected final String name;

    /** Agent系统提示词 */
    protected final String systemPrompt;

    /** LLM客户端 */
    protected final LLMClient llmClient;

    protected BioAgent(String name, String systemPrompt, LLMClient llmClient) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.llmClient = llmClient;
    }

    /**
     * 处理用户消息
     *
     * @param userMessage 用户消息
     * @param context     上下文信息（包含conversationId、userId、历史消息等）
     * @return 助手回复
     */
    public abstract String handle(String userMessage, Map<String, Object> context);

    /**
     * 获取Agent名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取系统提示词
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * 获取本Agent可用的工具定义列表
     * 子类可覆盖此方法以声明需要的工具
     *
     * @return 工具定义列表，默认为空列表（不使用工具）
     */
    public List<ToolDefinition> getTools() {
        return List.of();
    }
}
