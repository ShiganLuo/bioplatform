package com.bioplatform.agent.agents;

import com.bioplatform.agent.ChatMessage;
import com.bioplatform.agent.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通用问答Agent - 生物信息学知识问答
 * <p>
 * 功能：
 * - 解释生物信息学工具、算法和概念
 * - 回答基因组学、转录组学、蛋白质组学相关问题
 * - 解释生物学概念和实验设计
 * - 不需要特殊工具
 * </p>
 *
 * @author luosg
 */
@Component
public class QAAgent extends BioAgent {

    private static final Logger log = LoggerFactory.getLogger(QAAgent.class);

    private static final String SYSTEM_PROMPT = """
            你是生物信息学知识问答专家Agent。你的职责包括：
            
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
            
            请用专业但易懂的语言回答。如果问题超出生物信息学范围，请礼貌告知。
            回答时尽量具体、有条理，必要时给出参考文献或工具链接。
            """;

    public QAAgent(LLMClient llmClient) {
        super("qa", SYSTEM_PROMPT, llmClient);
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

        // QA Agent不使用工具，直接调用LLM
        String response = llmClient.chat(messages, systemPrompt);

        return response != null ? response : "抱歉，我暂时无法回答您的问题，请稍后再试。";
    }
}
