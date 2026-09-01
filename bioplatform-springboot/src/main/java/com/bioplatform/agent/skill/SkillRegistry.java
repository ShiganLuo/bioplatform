package com.bioplatform.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bioplatform.entity.SystemConfig;
import com.bioplatform.mapper.SystemConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill 注册中心
 * 1. 自动发现所有 Skill 实现（通过 Spring 注入）
 * 2. 加载数据库中 Agent 自建的 skill（skill:* 前缀的配置项）
 * 3. 按优先级拼装系统提示词
 *
 * @author luosg
 */
@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private static final String BASE_PROMPT = "你是一个专业的生物信息学助手，擅长解答基因组学、转录组学、蛋白质组学等生物信息学相关问题。\n" +
            "你拥有以下技能，根据用户问题自动选择合适的技能：\n";

    private static final String GLOBAL_RULES = "\n全局规则：\n" +
            "- 查询数据库时直接写SQL，不要先探索表结构（schema已提供）\n" +
            "- 用一条SQL获取数据，不要分多步\n" +
            "- 尽量1轮工具调用完成，最多不超过2轮\n" +
            "- 如果不确定，先问用户而不是猜测\n" +
            "- 遇到反复出现的查询模式，主动用 create_skill 工具创建新技能\n";

    private final List<Skill> codeSkills;
    private final List<Skill> dbSkills;
    private final String staticPrompt;

    public SkillRegistry(List<Skill> codeSkills, SystemConfigMapper configMapper,
                         ObjectMapper objectMapper) {
        this.codeSkills = codeSkills.stream()
                .sorted(Comparator.comparingInt(Skill::getPriority))
                .collect(Collectors.toList());

        // 从数据库加载 Agent 自建的 skill
        this.dbSkills = loadDbSkills(configMapper, objectMapper);

        // 合并所有 skill
        List<Skill> allSkills = new ArrayList<>();
        allSkills.addAll(codeSkills.stream()
                .sorted(Comparator.comparingInt(Skill::getPriority))
                .collect(Collectors.toList()));
        allSkills.addAll(dbSkills);

        // 预生成静态部分
        StringBuilder sb = new StringBuilder(BASE_PROMPT);
        for (Skill skill : allSkills) {
            sb.append("\n## ").append(skill.getName()).append("\n");
            sb.append(skill.getDescription()).append("\n");
            if (skill.getTriggerDescription() != null) {
                sb.append("触发条件：").append(skill.getTriggerDescription()).append("\n");
            }
            if (skill.getUsageHint() != null) {
                sb.append("使用提示：").append(skill.getUsageHint()).append("\n");
            }
        }
        sb.append(GLOBAL_RULES);
        this.staticPrompt = sb.toString();

        log.info("SkillRegistry 初始化: {} 个代码 Skill, {} 个数据库 Skill",
                codeSkills.size(), dbSkills.size());
    }

    /**
     * 从数据库加载 Agent 创建的 skill
     */
    private List<Skill> loadDbSkills(SystemConfigMapper configMapper, ObjectMapper objectMapper) {
        List<Skill> skills = new ArrayList<>();
        try {
            List<SystemConfig> allConfigs = configMapper.selectAll(new SystemConfig());
            for (SystemConfig config : allConfigs) {
                if (config.getConfigKey() != null && config.getConfigKey().startsWith("skill:")) {
                    try {
                        JsonNode node = objectMapper.readTree(config.getConfigValue());
                        String name = node.has("name") ? node.get("name").asText() : config.getConfigKey();
                        String desc = node.has("description") ? node.get("description").asText() : "";
                        String trigger = node.has("trigger") ? node.get("trigger").asText() : null;
                        String hint = node.has("hint") ? node.get("hint").asText() : null;
                        String fragment = node.has("fragment") ? node.get("fragment").asText() : null;

                        skills.add(new DbSkill(name, desc, trigger, hint, fragment));
                        log.info("加载数据库 Skill: {}", name);
                    } catch (Exception e) {
                        log.warn("解析数据库 Skill 失败: key={}", config.getConfigKey());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载数据库 Skill 列表失败: {}", e.getMessage());
        }
        return skills;
    }

    /**
     * 生成完整的系统提示词（静态部分 + 动态片段）
     */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder(staticPrompt);

        // 追加每个代码 skill 的动态片段（如数据库 schema）
        for (Skill skill : codeSkills) {
            String fragment = skill.getSystemPromptFragment();
            if (fragment != null && !fragment.isBlank()) {
                sb.append("\n### ").append(skill.getName()).append(" 上下文\n");
                sb.append(fragment).append("\n");
            }
        }

        return sb.toString();
    }

    public List<Skill> getSkills() {
        List<Skill> all = new ArrayList<>(codeSkills);
        all.addAll(dbSkills);
        return Collections.unmodifiableList(all);
    }

    /**
     * 数据库中 Agent 创建的 Skill 实现
     */
    private static class DbSkill implements Skill {
        private final String name;
        private final String description;
        private final String trigger;
        private final String hint;
        private final String fragment;

        DbSkill(String name, String description, String trigger, String hint, String fragment) {
            this.name = name;
            this.description = description;
            this.trigger = trigger;
            this.hint = hint;
            this.fragment = fragment;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public String getTriggerDescription() { return trigger; }
        @Override public String getUsageHint() { return hint; }
        @Override public String getSystemPromptFragment() { return fragment; }
        @Override public int getPriority() { return 150; } // 数据库 skill 优先级较低
    }
}
