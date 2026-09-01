package com.bioplatform.agent.tools.impl;

import com.bioplatform.agent.tools.Tool;
import com.bioplatform.mapper.SystemConfigMapper;
import com.bioplatform.entity.SystemConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建 Skill 工具 - 让 Agent 能根据业务需要自动创建新 skill
 * 新 skill 持久化到数据库，重启后自动加载
 *
 * @author luosg
 */
@Component
public class CreateSkillTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CreateSkillTool.class);

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    public CreateSkillTool(SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper) {
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "create_skill";
    }

    @Override
    public String getDescription() {
        return "创建新的 Agent 技能（Skill），持久化到数据库。适用于发现新的查询模式、常用操作等场景。";
    }

    @Override
    public String getTriggerDescription() {
        return "当发现用户反复询问某类问题、或需要封装新的查询模式时，主动创建 skill。" +
                "例如：用户多次查询基因表达数据 → 创建 gene_expression_query skill。";
    }

    @Override
    public String getUsageHint() {
        return "创建 skill 后，它会在下次对话中自动生效。skill 包含：名称、触发条件、使用提示、SQL模板等。";
    }

    @Override
    public int getPriority() {
        return 200; // 低优先级，不常使用
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "技能名称（英文下划线命名，如 gene_query）");
        properties.put("name", nameProp);

        Map<String, Object> descProp = new HashMap<>();
        descProp.put("type", "string");
        descProp.put("description", "技能简介（一句话描述）");
        properties.put("description", descProp);

        Map<String, Object> triggerProp = new HashMap<>();
        triggerProp.put("type", "string");
        triggerProp.put("description", "触发条件（什么时候该用这个技能）");
        properties.put("trigger", triggerProp);

        Map<String, Object> hintProp = new HashMap<>();
        hintProp.put("type", "string");
        hintProp.put("description", "使用提示（怎么用这个技能，SQL模板等）");
        properties.put("hint", hintProp);

        Map<String, Object> fragmentProp = new HashMap<>();
        fragmentProp.put("type", "string");
        fragmentProp.put("description", "系统提示词片段（可选，注入到系统提示词中的额外上下文）");
        properties.put("fragment", fragmentProp);

        schema.put("properties", properties);
        schema.put("required", List.of("name", "description", "trigger", "hint"));
        return schema;
    }

    @Override
    public String execute(Map<String, String> args) {
        String name = args.get("name");
        String description = args.get("description");
        String trigger = args.get("trigger");
        String hint = args.get("hint");
        String fragment = args.get("fragment");

        if (name == null || name.isBlank()) {
            return toJson(-1, "缺少必需参数: name");
        }

        // 构建 skill JSON
        try {
            Map<String, Object> skill = new LinkedHashMap<>();
            skill.put("name", name);
            skill.put("description", description);
            skill.put("trigger", trigger);
            skill.put("hint", hint);
            if (fragment != null && !fragment.isBlank()) {
                skill.put("fragment", fragment);
            }
            skill.put("createdAt", java.time.LocalDateTime.now().toString());

            String skillJson = objectMapper.writeValueAsString(skill);

            // 存储到数据库（key = skill:<name>）
            String configKey = "skill:" + name;
            SystemConfig existing = systemConfigMapper.selectByKey(configKey);
            if (existing != null) {
                existing.setConfigValue(skillJson);
                systemConfigMapper.updateById(existing);
            } else {
                SystemConfig config = new SystemConfig();
                config.setConfigKey(configKey);
                config.setConfigValue(skillJson);
                systemConfigMapper.insert(config);
            }

            log.info("创建 Skill: name={}, trigger={}", name, trigger);
            return toJson(0, "Skill '" + name + "' 创建成功，下次对话自动生效。");

        } catch (Exception e) {
            log.error("创建 Skill 失败", e);
            return toJson(-1, "创建失败: " + e.getMessage());
        }
    }

    private String toJson(int exitCode, String message) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("exit_code", exitCode);
            result.put("success", exitCode == 0);
            result.put("output", message);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"exit_code\":" + exitCode + ",\"output\":\"" + message.replace("\"", "\\\"") + "\"}";
        }
    }
}
