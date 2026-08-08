package com.bioplatform.agent;

import java.util.Collections;
import java.util.List;

/**
 * LLM响应封装类
 *
 * @author luosg
 */
public class LLMResponse {

    /** 文本内容 */
    private String content;

    /** 工具调用列表 */
    private List<ToolCall> toolCalls;

    public LLMResponse() {
        this.toolCalls = Collections.emptyList();
    }

    public LLMResponse(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls != null ? toolCalls : Collections.emptyList();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    /**
     * 判断是否包含工具调用
     */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
