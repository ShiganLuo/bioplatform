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
 * 数据分析Agent - 专注于生物信息学数据分析
 * <p>
 * 功能：
 * - 解释VCF/BAM/FASTA等文件格式
 * - 根据数据类型建议分析流程
 * - 读取文件信息、执行BLAST搜索、格式转换等工具调用
 * </p>
 *
 * @author luosg
 */
@Component
public class DataAnalysisAgent extends BioAgent {

    private static final Logger log = LoggerFactory.getLogger(DataAnalysisAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是生物信息学数据分析专家Agent。你的职责包括：
            
            1. 文件格式解释：
               - VCF (Variant Call Format): 变异检测结果文件，包含SNP和Indel信息
               - BAM/SAM: 比对文件，存储reads比对到参考基因组的信息
               - FASTA: 序列文件，存储DNA或蛋白质序列
               - FASTQ: 测序原始数据文件，包含序列和质量值
               - BED: 基因组区间文件
               - GFF/GTF: 基因注释文件
               - BigWig: 基因组信号数据文件
            
            2. 分析流程建议：
               - 根据数据类型（WGS/WES/RNA-seq/ChIP-seq等）推荐分析流程
               - 根据物种和基因组版本推荐合适的参考数据库
               - 根据分析目标（变异检测/差异表达/富集分析等）推荐工具组合
            
            3. 可用工具：
               - file_info: 读取文件元数据信息
               - format_info: 查询文件格式的详细说明
            
            请用专业但易懂的语言回答，必要时给出具体的操作步骤建议。
            """;

    private final AgentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public DataAnalysisAgent(LLMClient llmClient, AgentToolExecutor toolExecutor, ObjectMapper objectMapper) {
        super("data_analysis", SYSTEM_PROMPT, llmClient);
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

        // 处理工具调用循环（最多5轮，防止死循环）
        int toolRounds = 0;
        while (response.hasToolCalls() && toolRounds < 5) {
            toolRounds++;
            log.info("DataAnalysisAgent工具调用第{}轮: {}个工具", toolRounds, response.getToolCalls().size());

            // 将助手消息（含工具调用）加入上下文
            messages.add(new ChatMessage("assistant", response.getContent(), null));

            // 执行每个工具调用并收集结果
            for (ToolCall toolCall : response.getToolCalls()) {
                String result = executeToolCall(toolCall);
                messages.add(ChatMessage.tool(toolCall.id(), result));
            }

            // 再次调用LLM获取基于工具结果的回复
            response = llmClient.chatWithTools(messages, systemPrompt, tools);
        }

        return response.getContent() != null ? response.getContent() : "抱歉，我无法处理您的请求。";
    }

    @Override
    public List<ToolDefinition> getTools() {
        List<ToolDefinition> allTools = toolExecutor.getAllToolDefinitions();
        // 数据分析相关工具 + shell_execute 万能工具
        return allTools.stream()
                .filter(t -> "file_info".equals(t.name())
                        || "format_info".equals(t.name())
                        || "file_search".equals(t.name())
                        || "shell_execute".equals(t.name()))
                .toList();
    }

    /**
     * 执行工具调用
     */
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
