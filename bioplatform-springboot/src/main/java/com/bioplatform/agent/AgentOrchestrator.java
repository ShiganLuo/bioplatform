package com.bioplatform.agent;

import com.bioplatform.agent.agents.BioAgent;
import com.bioplatform.agent.agents.DataAnalysisAgent;
import com.bioplatform.agent.agents.PipelineAgent;
import com.bioplatform.agent.agents.QAAgent;
import com.bioplatform.entity.AgentConversation;
import com.bioplatform.entity.AgentMessage;
import com.bioplatform.mapper.AgentConversationMapper;
import com.bioplatform.mapper.AgentMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Agent编排器 - 多Agent协调与路由
 * <p>
 * 主要职责：
 * 1. 意图识别：分析用户消息，判断应路由到哪个Agent
 * 2. 上下文管理：加载和维护对话历史上下文
 * 3. Agent调度：将请求分发到合适的Agent并返回结果
 * 4. 消息持久化：保存用户消息和Agent回复
 * </p>
 *
 * @author luosg
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    /** 上下文消息数量限制 */
    private static final int CONTEXT_MESSAGE_LIMIT = 20;

    private final DataAnalysisAgent dataAnalysisAgent;
    private final PipelineAgent pipelineAgent;
    private final QAAgent qaAgent;
    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;

    /** Agent注册表 */
    private final Map<String, BioAgent> agentRegistry;

    // 意图识别关键词模式
    private static final Pattern PIPELINE_PATTERN = Pattern.compile(
            "(?i)(流水线|pipeline|流程|分析流程|workflow|workflow|运行|执行|配置流程)");
    private static final Pattern DATA_ANALYSIS_PATTERN = Pattern.compile(
            "(?i)(文件格式|VCF|BAM|FASTA|FASTQ|BED|GFF|GTF|比对|变异|格式|数据文件|文件信息|序列)");
    private static final Pattern QC_PATTERN = Pattern.compile(
            "(?i)(质量控制|QC|质控|fastqc|multiqc|过滤|trim|质量)");

    public AgentOrchestrator(DataAnalysisAgent dataAnalysisAgent,
                             PipelineAgent pipelineAgent,
                             QAAgent qaAgent,
                             AgentConversationMapper conversationMapper,
                             AgentMessageMapper messageMapper) {
        this.dataAnalysisAgent = dataAnalysisAgent;
        this.pipelineAgent = pipelineAgent;
        this.qaAgent = qaAgent;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;

        // 初始化Agent注册表
        this.agentRegistry = new LinkedHashMap<>();
        this.agentRegistry.put(dataAnalysisAgent.getName(), dataAnalysisAgent);
        this.agentRegistry.put(pipelineAgent.getName(), pipelineAgent);
        this.agentRegistry.put(qaAgent.getName(), qaAgent);

        log.info("AgentOrchestrator初始化完成，注册{}个Agent: {}", agentRegistry.size(), agentRegistry.keySet());
    }

    /**
     * 处理用户消息（主入口）
     *
     * @param conversationId 对话ID
     * @param userMessage    用户消息
     * @param userId         用户ID
     * @return 助手回复内容
     */
    @Transactional
    public String processMessage(Long conversationId, String userMessage, Long userId) {
        log.info("收到用户消息: conversationId={}, userId={}, message={}",
                conversationId, userId, userMessage.length() > 100 ? userMessage.substring(0, 100) + "..." : userMessage);

        // 1. 验证对话存在性
        AgentConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("对话不存在: conversationId=" + conversationId);
        }

        // 2. 保存用户消息
        AgentMessage userMsg = new AgentMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        messageMapper.insert(userMsg);

        // 3. 加载对话历史上下文
        List<AgentMessage> historyMessages = messageMapper.selectRecentByConversationId(
                conversationId, CONTEXT_MESSAGE_LIMIT);
        List<ChatMessage> history = convertToChatMessages(historyMessages);

        // 4. 意图识别，选择Agent
        BioAgent selectedAgent = detectAgent(userMessage);
        log.info("意图识别结果: agent={}", selectedAgent.getName());

        // 5. 构建上下文并调用Agent
        Map<String, Object> context = new HashMap<>();
        context.put("conversationId", conversationId);
        context.put("userId", userId);
        context.put("history", history);
        context.put("userMessage", userMessage);

        String assistantReply;
        try {
            assistantReply = selectedAgent.handle(userMessage, context);
        } catch (Exception e) {
            log.error("Agent处理异常: agent={}", selectedAgent.getName(), e);
            assistantReply = "处理您的请求时出现了错误，请稍后再试。错误信息: " + e.getMessage();
        }

        // 6. 保存助手回复
        AgentMessage assistantMsg = new AgentMessage();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(assistantReply);
        messageMapper.insert(assistantMsg);

        // 7. 更新对话时间
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        log.info("消息处理完成: conversationId={}, agent={}, replyLength={}",
                conversationId, selectedAgent.getName(), assistantReply.length());

        return assistantReply;
    }

    /**
     * 意图识别 - 根据用户消息选择最合适的Agent
     *
     * @param userMessage 用户消息
     * @return 选定的Agent
     */
    private BioAgent detectAgent(String userMessage) {
        // 优先匹配流水线相关
        if (PIPELINE_PATTERN.matcher(userMessage).find()) {
            log.debug("匹配到流水线意图");
            return pipelineAgent;
        }

        // 匹配数据分析/文件格式相关
        if (DATA_ANALYSIS_PATTERN.matcher(userMessage).find()) {
            log.debug("匹配到数据分析意图");
            return dataAnalysisAgent;
        }

        // 匹配质量控制相关（也归入数据分析Agent）
        if (QC_PATTERN.matcher(userMessage).find()) {
            log.debug("匹配到质控意图，归入数据分析Agent");
            return dataAnalysisAgent;
        }

        // 默认使用QA Agent
        log.debug("未匹配到特定意图，使用QA Agent");
        return qaAgent;
    }

    /**
     * 将数据库消息实体转换为ChatMessage记录
     */
    private List<ChatMessage> convertToChatMessages(List<AgentMessage> dbMessages) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (AgentMessage msg : dbMessages) {
            // 跳过刚保存的用户消息（历史中已包含）
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                chatMessages.add(ChatMessage.user(msg.getContent()));
            } else if ("assistant".equals(msg.getRole()) && msg.getContent() != null) {
                chatMessages.add(ChatMessage.assistant(msg.getContent()));
            }
        }
        return chatMessages;
    }

    /**
     * 获取所有已注册Agent的名称
     */
    public Set<String> getRegisteredAgentNames() {
        return Collections.unmodifiableSet(agentRegistry.keySet());
    }

    /**
     * 根据名称获取Agent
     */
    public BioAgent getAgent(String name) {
        return agentRegistry.get(name);
    }
}
