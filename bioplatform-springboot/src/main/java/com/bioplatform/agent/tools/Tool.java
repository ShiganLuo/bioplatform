package com.bioplatform.agent.tools;

import com.bioplatform.agent.skill.Skill;

import java.util.Map;

/**
 * Agent工具接口
 * 继承 Skill 接口，每个工具自动成为 skill
 * 工具只需实现 Tool 的4个方法，Skill 方法有默认实现
 *
 * @author luosg
 */
public interface Tool extends Skill {

    /**
     * 获取工具参数定义（JSON Schema格式）
     */
    Map<String, Object> getParameters();

    /**
     * 执行工具
     */
    String execute(Map<String, String> args);

    // ===== Skill 默认实现 =====

    @Override
    default String getTriggerDescription() {
        return getDescription();
    }

    @Override
    default String getUsageHint() {
        return null;
    }

    @Override
    default String getSystemPromptFragment() {
        return null;
    }

    @Override
    default int getPriority() {
        return 100;
    }
}
