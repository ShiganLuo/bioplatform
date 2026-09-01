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
 * 通用问答Agent - 生物信息学知识问答 + 平台业务查询
 * <p>
 * 功能：
 * - 解释生物信息学工具、算法和概念
 * - 回答基因组学、转录组学、蛋白质组学相关问题
 * - 通过 shell_execute 访问服务器数据
 * - 通过专用工具查询平台业务数据
 * </p>
 *
 * @author luosg
 */
@Component
public class QAAgent extends BioAgent {

    private static final Logger log = LoggerFactory.getLogger(QAAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是生物信息学知识问答专家Agent，同时具备平台业务数据查询能力。你的职责包括：
            
            1. 工具与算法解释：
               - BWA、Bowtie2、STAR等比对工具的原理和使用场景
               - GATK、FreeBayes、Samtools等变异检测工具
               - DESeq2、edgeR等差异表达分析工具
               - BLAST、HMMER等序列搜索工具
               - 各种QC工具（FastQC、MultiQC等）
            
            2. 生物学概念解释：
               - 基因组学：WGS、WES、群体遗传学、GWAS
               - 转录组学：RNA-seq、单细胞测序、空间转录组
               - 表观基因组学：ChIP-seq、ATAC-seq、甲基化测序
               - 蛋白质组学：质谱分析、蛋白质结构预测
               - 宏基因组学：16S rRNA、Shotgun测序
            
            3. 分析流程说明：
               - 标准分析流程的每一步及其作用
               - 质量控制的重要性和方法
               - 结果解读和下游分析建议
            
            4. 实验设计建议：
               - 测序深度和覆盖度建议
               - 生物学重复数量建议
               - 对照组设计
            
            5. 平台业务查询：
               - 查询用户的项目列表和详情
               - 搜索和浏览数据文件
               - 查看流水线执行记录和状态
               - 获取平台概况统计
            
            你可以使用以下工具：
            - shell_execute: 在服务器上执行 shell 命令（万能工具，可查文件系统、数据库、运行任何命令）
            - pipeline_list: 列出可用的分析流水线
            - file_info: 按ID查看文件元数据信息
            - format_info: 查询文件格式的详细说明
            
            使用 shell_execute 时的常用命令参考：
            - 查看项目: mysql -u root bioplatform -e "SELECT id,name,organism,status FROM projects"
            - 查看文件: mysql -u root bioplatform -e "SELECT id,name,file_type,organism FROM data_files"
            - 查看执行记录: mysql -u root bioplatform -e "SELECT id,pipeline_id,status,created_at FROM pipeline_executions ORDER BY created_at DESC LIMIT 10"
            - 浏览文件系统: ls -la /data/
            - 检查磁盘: df -h
            - 生信工具: samtools flagstat /path/to/file.bam
            
            重要规则：
            1. 当用户询问平台数据相关问题时，必须先调用工具获取真实数据再回答，不要凭空猜测
            2. 优先使用专用工具（pipeline_list, file_info），它们返回结构化数据更可靠
            3. 只有当专用工具无法满足需求时，才使用 shell_execute
            4. 数据库查询使用 SELECT，工具已内置安全拦截，禁止写操作
            5. 对大文件使用 head/tail 限制输出，不要 cat 整个大文件
            
            请用专业但易懂的语言回答。如果问题超出生物信息学范围，请礼貌告知。
            回答时尽量具体、有条理，必要时给出参考文献或工具链接。
            """;

    private final AgentToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    public QAAgent(LLMClient llmClient, AgentToolExecutor toolExecutor, ObjectMapper objectMapper) {
        super("qa", SYSTEM_PROMPT, llmClient);
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

        // 带工具定义调用 LLM
        List<ToolDefinition> tools = getTools();
        LLMResponse response = llmClient.chatWithTools(messages, systemPrompt, tools);

        // 工具调用循环（最多5轮，防止死循环）
        int toolRounds = 0;
        while (response.hasToolCalls() && toolRounds < 5) {
            toolRounds++;
            log.info("QAAgent工具调用第{}轮: {}个工具", toolRounds, response.getToolCalls().size());

            // 将助手消息（含工具调用）加入上下文
            messages.add(new ChatMessage("assistant", response.getContent(), null));

            // 执行每个工具调用并收集结果
            for (ToolCall toolCall : response.getToolCalls()) {
                String result = executeToolCall(toolCall);
                messages.add(ChatMessage.tool(toolCall.id(), result));
            }

            // 再次调用 LLM 获取基于工具结果的回复
            response = llmClient.chatWithTools(messages, systemPrompt, tools);
        }

        return response.getContent() != null ? response.getContent()
                : "抱歉，我暂时无法回答您的问题，请稍后再试。";
    }

    @Override
    public List<ToolDefinition> getTools() {
        // QA Agent 拥有全部工具
        return toolExecutor.getAllToolDefinitions();
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
