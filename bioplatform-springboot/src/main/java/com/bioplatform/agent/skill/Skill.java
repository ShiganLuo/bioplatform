package com.bioplatform.agent.skill;

/**
 * Agent Skill 接口
 * 每个工具/技能实现此接口，自描述使用方式，系统提示词自动拼装
 * 参照 Hermes Agent 的 skill 架构设计
 *
 * @author luosg
 */
public interface Skill {

    /**
     * 技能名称（唯一标识）
     */
    String getName();

    /**
     * 技能简介（一行描述）
     */
    String getDescription();

    /**
     * 触发条件描述（告诉 LLM 什么时候该用这个技能）
     * 例："当用户询问数据库中的项目、用户、任务等业务数据时"
     */
    String getTriggerDescription();

    /**
     * 使用提示（告诉 LLM 怎么用这个技能）
     * 例："直接写 SQL SELECT 查询，不要先 SHOW TABLES"
     */
    String getUsageHint();

    /**
     * 贡献到系统提示词的片段
     * 返回 null 表示不需要额外的系统提示词（仅靠 trigger + hint 即可）
     * 例：数据库 schema、文件系统结构等动态上下文
     */
    default String getSystemPromptFragment() {
        return null;
    }

    /**
     * 优先级（数字越小越靠前，默认 100）
     * 用于控制 skill 在系统提示词中的排列顺序
     */
    default int getPriority() {
        return 100;
    }
}
