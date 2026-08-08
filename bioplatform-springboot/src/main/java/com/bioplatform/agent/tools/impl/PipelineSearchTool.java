package com.bioplatform.agent.tools.impl;

import com.bioplatform.agent.tools.Tool;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.mapper.PipelineMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线搜索工具 - 搜索可用的分析流水线
 *
 * @author luosg
 */
@Component
public class PipelineSearchTool implements Tool {

    private final PipelineMapper pipelineMapper;
    private final ObjectMapper objectMapper;

    public PipelineSearchTool(PipelineMapper pipelineMapper, ObjectMapper objectMapper) {
        this.pipelineMapper = pipelineMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "pipeline_list";
    }

    @Override
    public String getDescription() {
        return "搜索和列出可用的生物信息学分析流水线。" +
                "可按名称或分类筛选。返回流水线列表，包含名称、描述、分类等信息。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> keywordProp = new HashMap<>();
        keywordProp.put("type", "string");
        keywordProp.put("description", "搜索关键词（按流水线名称模糊搜索）");
        properties.put("keyword", keywordProp);

        Map<String, Object> categoryProp = new HashMap<>();
        categoryProp.put("type", "string");
        categoryProp.put("description", "流水线分类，如: variant_calling, expression, qc, alignment 等");
        properties.put("category", categoryProp);

        schema.put("properties", properties);
        return schema;
    }

    @Override
    public String execute(Map<String, String> args) {
        try {
            String keyword = args.get("keyword");
            String category = args.get("category");

            List<Pipeline> pipelines;

            if (keyword != null && !keyword.isEmpty()) {
                // 按名称搜索
                pipelines = pipelineMapper.searchByName(keyword, category, null);
            } else if (category != null && !category.isEmpty()) {
                // 按分类查询
                pipelines = pipelineMapper.selectByCategory(category);
            } else {
                // 查询所有
                pipelines = pipelineMapper.selectAll(null);
            }

            // 构建结果
            Map<String, Object> result = new HashMap<>();
            result.put("total", pipelines.size());
            result.put("pipelines", pipelines.stream().map(p -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", p.getId());
                info.put("name", p.getName());
                info.put("description", p.getDescription());
                info.put("category", p.getCategory());
                info.put("docker_image", p.getDockerImage());
                info.put("timeout", p.getTimeout());
                return info;
            }).toList());

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"搜索流水线失败: " + e.getMessage() + "\"}";
        }
    }
}
