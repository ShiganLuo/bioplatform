package com.bioplatform.agent.agents;

import com.bioplatform.agent.*;
import com.bioplatform.agent.tools.AgentToolExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流水线Agent - 专注于流水线推荐与配置
 * <p>
 * 功能：
 * - 根据物种、数据类型、分析目标推荐流水线
 * - 帮助配置流水线参数
 * - 列出可用流水线、查看流水线详情、执行流水线
 * </p>
 *
 * @author luosg
 */
@Component
public class PipelineAgent extends BioAgent {

    private static final Logger log = LoggerFactory.getLogger(PipelineAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是生物信息学流水线推荐与配置专家Agent。你的职责包括：
            
            1. 流水线推荐：
               - 根据物种（人、小鼠、拟南芥等）推荐合适的分析流水线
               - 根据数据类型（WGS、WES、RNA-seq、ChIP-seq、ATAC-seq等）推荐流水线
               - 根据分析目标（变异检测、基因表达定量、差异分析、富集分析等）推荐流水线
               - 根据参考基因组版本推荐兼容的流水线
            
            2. 流水线配置：
               - 解释每个流水线步骤的作用和参数含义
               - 帮助用户配置流水线参数（线程数、内存、阈值等）
               - 根据数据特点给出参数建议
               - 检查配置的合理性和完整性
            
            3. 流水线管理：
               - 列出所有可用流水线及其描述
               - 查看流水线的详细信息和配置
               - 执行流水线并跟踪状态
            
            4. 可用工具：
               - pipeline_list: 列出可用流水线
               - pipeline_detail: 查看流水线详情
               - file_info: 查看文件信息（用于确认输入数据）
            
            请用专业但易懂的语言回答，给出具体的配置建议和操作步骤。
            """;

    private final AgentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public PipelineAgent(LLMClient llmClient, AgentToolExecutor toolExecutor, ObjectMapper objectMapper) {
        super("pipeline", SYSTEM_PROMPT, llmClient);
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public String handle(String userMessage, Map<String, Object> context) {
        List<ChatMessage> messages = new ArrayList<>();

        // 从上下文中加载历史消息
        @SuppressWarnings("unchecked")
        List<ChatMessage> history = (List<ChatMessage>) context.getOrDefault("history", List.of());
        messages.addAll(history);

        // 添加当前用户消息
        messages.add(ChatMessage.user(userMessage));

        // 第一次调用LLM（带工具定义）
        List<ToolDefinition> tools = getTools();
        LLMResponse response = llmClient.chatWithTools(messages, systemPrompt, tools);

        // 处理工具调用循环（最多5轮）
        int toolRounds = 0;
        while (response.hasToolCalls() && toolRounds < 5) {
            toolRounds++;
            log.info("PipelineAgent工具调用第{}轮: {}个工具", toolRounds, response.getToolCalls().size());

            List<ChatMessage.ToolCallReference> toolCallRefs = response.getToolCalls().stream()
                    .map(tc -> new ChatMessage.ToolCallReference(tc.id(), tc.name(), tc.arguments()))
                    .toList();
            messages.add(ChatMessage.assistantWithToolCalls(response.getContent(), toolCallRefs));

            for (ToolCall toolCall : response.getToolCalls()) {
                String result = executeToolCall(toolCall);
                messages.add(ChatMessage.tool(toolCall.id(), result));
            }

            response = llmClient.chatWithTools(messages, systemPrompt, tools);
        }

        return response.getContent() != null ? response.getContent() : "抱歉，我无法处理您的流水线相关请求。";
    }

    @Override
    public List<ToolDefinition> getTools() {
        List<ToolDefinition> allTools = toolExecutor.getAllToolDefinitions();
        return allTools.stream()
                .filter(t -> "pipeline_list".equals(t.name())
                        || "pipeline_detail".equals(t.name())
                        || "file_info".equals(t.name())
                        || "shell_execute".equals(t.name()))
                .toList();
    }

    private String executeToolCall(ToolCall toolCall) {
        try {
            Map<String, String> args = objectMapper.readValue(
                    toolCall.arguments(), new TypeReference<>() {});
            return toolExecutor.executeTool(toolCall.name(), args);
        } catch (JsonProcessingException e) {
            log.error("解析工具参数失败: {}", toolCall.arguments(), e);
            return "{\"error\": \"参数解析失败\"}";
        }
    }
}
