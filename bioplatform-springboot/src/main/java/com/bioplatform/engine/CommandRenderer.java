package com.bioplatform.engine;

import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.WorkflowTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 命令渲染器
 * 把 workflow_templates.command_template 中的 {{placeholder}} 占位符
 * 替换为执行时的真实值,产出一条可直接交给 bash 执行的命令。
 *
 * 支持的占位符:
 *   {{executionId}} {{pipelineId}} {{projectId}}   业务 ID
 *   {{image}}                                      pipeline.docker_image
 *   {{data_root}}                                  上传卷在宿主机上的绝对路径(配置 bioplatform.storage.data-root)
 *   {{output_dir}}                                 结果目录(相对 data_root): {projectId}/results/exec_{executionId}
 *   {{inputParams.xxx}}                            执行参数 JSON 的 dot-notation 取值
 *   {{config.xxx}}                                 模板默认配置(configJson)的 dot-notation 取值
 *
 * 未解析的占位符直接抛异常(快速失败),不允许带着字面 {{}} 进执行端。
 *
 * @author luosg
 */
@Component
public class CommandRenderer {

    private static final Logger log = LoggerFactory.getLogger(CommandRenderer.class);

    private final ObjectMapper objectMapper;

    /** 上传卷宿主机路径,容器式命令的 -v 挂载用(环境变量 BIPLATFORM_STORAGE_DATAROOT) */
    @Value("${bioplatform.storage.dataRoot:}")
    private String dataRoot;

    public CommandRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 渲染结果
     */
    public record RenderedCommand(String command, String outputDir) {}

    public RenderedCommand render(WorkflowTemplate template, Pipeline pipeline, PipelineExecution execution) {
        String tpl = template.getCommandTemplate();
        if (tpl == null || tpl.isBlank()) {
            throw new IllegalArgumentException("模板未配置命令模板(command_template)");
        }

        String outputDir = execution.getProjectId() + "/results/exec_" + execution.getId();

        Map<String, String> values = new HashMap<>();
        values.put("executionId", String.valueOf(execution.getId()));
        values.put("pipelineId", String.valueOf(execution.getPipelineId()));
        values.put("projectId", execution.getProjectId() != null ? String.valueOf(execution.getProjectId()) : "");
        values.put("image", pipeline.getDockerImage() != null ? pipeline.getDockerImage() : "");
        values.put("data_root", dataRoot != null ? dataRoot : "");
        values.put("output_dir", outputDir);
        // 用户执行参数与模板默认配置均可按 dot-notation 取值
        flattenJsonInto(execution.getInputParams(), "inputParams.", values);
        flattenJsonInto(pipeline.getConfigJson(), "config.", values);

        StringBuilder unresolved = new StringBuilder();
        StringBuffer rendered = new StringBuffer();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\{\\s*([^}]+?)\\s*\\}\\}").matcher(tpl);
        while (m.find()) {
            String key = m.group(1);
            String val = values.get(key);
            if (val == null) {
                unresolved.append("{{").append(key).append("}} ");
            } else {
                m.appendReplacement(rendered, java.util.regex.Matcher.quoteReplacement(val));
            }
        }
        m.appendTail(rendered);
        if (unresolved.length() > 0) {
            throw new IllegalArgumentException("命令模板占位符无法解析: " + unresolved.toString().trim());
        }

        String command = rendered.toString();
        log.info("命令渲染完成: executionId={}, command={}", execution.getId(), command);
        return new RenderedCommand(command, outputDir);
    }

    /**
     * 把 JSON 字符串拍平成 dot-notation 键值对(嵌套对象递归,数组直接 toString)
     */
    private void flattenJsonInto(String json, String prefix, Map<String, String> out) {
        if (json == null || json.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(json);
            flattenNode("", root, prefix, out);
        } catch (Exception e) {
            log.warn("JSON 解析失败,跳过 {} 占位符: {}", prefix, e.getMessage());
        }
    }

    private void flattenNode(String path, JsonNode node, String prefix, Map<String, String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String childPath = path.isEmpty() ? e.getKey() : path + "." + e.getKey();
                flattenNode(childPath, e.getValue(), prefix, out);
            }
        } else {
            String key = prefix + path;
            if (!key.endsWith(prefix)) { // 空 JSON 根不产生裸 prefix 键
                out.put(key, node.isValueNode() ? node.asText() : node.toString());
            }
        }
    }
}
