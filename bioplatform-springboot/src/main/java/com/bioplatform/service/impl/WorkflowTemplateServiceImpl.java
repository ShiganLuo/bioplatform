package com.bioplatform.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO.CreateRequest;
import com.bioplatform.dto.admin.AdminWorkflowTemplateDTO.UpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.entity.WorkflowTemplate;
import com.bioplatform.mapper.WorkflowTemplateMapper;
import com.bioplatform.service.WorkflowTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 工作流模板服务实现
 *
 * @author luosg
 */
@Service
public class WorkflowTemplateServiceImpl implements WorkflowTemplateService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTemplateServiceImpl.class);

    private final WorkflowTemplateMapper templateMapper;

    public WorkflowTemplateServiceImpl(WorkflowTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    @Override
    public WorkflowTemplate createTemplate(CreateRequest request) {
        WorkflowTemplate template = new WorkflowTemplate();
        template.setName(request.name());
        template.setDescription(request.description());
        template.setType(request.type());
        template.setCategory(request.category());
        template.setConfigTemplate(request.configTemplate());
        template.setSchemaJson(request.schemaJson());
        template.setSnakemakePath(request.snakemakePath());
        template.setIcon(request.icon());
        template.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        template.setEnabled(true);

        templateMapper.insert(template);
        log.info("创建模板成功: id={}, name={}", template.getId(), template.getName());
        return template;
    }

    @Override
    public void updateTemplate(UpdateRequest request) {
        WorkflowTemplate template = templateMapper.selectById(request.id());
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }

        template.setName(request.name());
        template.setDescription(request.description());
        template.setType(request.type());
        template.setCategory(request.category());
        template.setConfigTemplate(request.configTemplate());
        template.setSchemaJson(request.schemaJson());
        template.setSnakemakePath(request.snakemakePath());
        template.setIcon(request.icon());
        if (request.sortOrder() != null) template.setSortOrder(request.sortOrder());
        if (request.enabled() != null) template.setEnabled(request.enabled());

        templateMapper.updateById(template);
        log.info("更新模板成功: id={}", template.getId());
    }

    @Override
    public void deleteTemplate(Long id) {
        WorkflowTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        templateMapper.deleteById(id);
        log.info("删除模板成功: id={}", id);
    }

    @Override
    public WorkflowTemplate getTemplateById(Long id) {
        return templateMapper.selectById(id);
    }

    @Override
    public PageResult listTemplates(String type, String category, int page, int size) {
        PageHelper.startPage(page, size);
        WorkflowTemplate query = new WorkflowTemplate();
        query.setType(type);
        query.setCategory(category);
        List<WorkflowTemplate> list = templateMapper.selectAll(query);
        PageInfo<WorkflowTemplate> pageInfo = new PageInfo<>(list);
        return PageResult.of(pageInfo.getTotal(), page, size, list);
    }

    @Override
    public int importFromOmics(String omicsDir) {
        Path configDir = Paths.get(omicsDir, "config");
        if (!Files.isDirectory(configDir)) {
            throw new IllegalArgumentException("Omics config 目录不存在: " + configDir);
        }

        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.json")) {
            for (Path jsonFile : stream) {
                String fileName = jsonFile.getFileName().toString();
                // 跳过 schema 文件和通用 schema
                if (fileName.endsWith(".schema.json") || fileName.equals("schema.json") || fileName.equals("schema.schema.json")) {
                    continue;
                }

                String workflowName = fileName.replace(".json", "");
                Path schemaFile = configDir.resolve(workflowName + ".schema.json");
                if (!Files.exists(schemaFile)) {
                    log.warn("跳过 {}，无对应 schema 文件", workflowName);
                    continue;
                }

                try {
                    // 读取 JSON 文件
                    String configJson = Files.readString(jsonFile);
                    String schemaJson = Files.readString(schemaFile);

                    // 判断类型：subworkflow 存在则是 pipeline，否则是 task
                    Path subworkflowFile = Paths.get(omicsDir, "subworkflow", workflowName + ".smk");
                    String type = Files.exists(subworkflowFile) ? "pipeline" : "task";

                    // 确定 snakemake 路径
                    String snakemakePath;
                    if (type.equals("pipeline")) {
                        snakemakePath = "subworkflow/" + workflowName + ".smk";
                    } else {
                        snakemakePath = "modules/" + workflowName + "/" + workflowName + ".smk";
                    }

                    // 检查是否已存在同名模板
                    WorkflowTemplate existing = new WorkflowTemplate();
                    existing.setName(workflowName);
                    List<WorkflowTemplate> dupes = templateMapper.selectAll(existing);
                    if (!dupes.isEmpty()) {
                        log.info("模板 {} 已存在，更新配置", workflowName);
                        WorkflowTemplate dup = dupes.get(0);
                        dup.setConfigTemplate(configJson);
                        dup.setSchemaJson(schemaJson);
                        dup.setSnakemakePath(snakemakePath);
                        dup.setType(type);
                        templateMapper.updateById(dup);
                    } else {
                        WorkflowTemplate template = new WorkflowTemplate();
                        template.setName(workflowName);
                        template.setDescription("从 Omics 仓库导入: " + workflowName);
                        template.setType(type);
                        template.setCategory(guessCategory(workflowName));
                        template.setConfigTemplate(configJson);
                        template.setSchemaJson(schemaJson);
                        template.setSnakemakePath(snakemakePath);
                        template.setSortOrder(0);
                        template.setEnabled(true);
                        templateMapper.insert(template);
                    }
                    count++;
                    log.info("导入模板: {} (type={})", workflowName, type);
                } catch (Exception e) {
                    log.error("导入模板 {} 失败，跳过: {}", workflowName, e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("读取 Omics 目录失败: " + e.getMessage(), e);
        }

        return count;
    }

    /**
     * 根据工作流名称猜测分类
     */
    private String guessCategory(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("rna") || lower.contains("scrna")) return "转录组";
        if (lower.contains("mutation") || lower.contains("snp") || lower.contains("pacvar")) return "变异检测";
        if (lower.contains("clip") || lower.contains("merip") || lower.contains("peak")) return "表观遗传学";
        if (lower.contains("protein") || lower.contains("ms")) return "蛋白质组";
        if (lower.contains("coCulture")) return "共培养";
        return "其他";
    }
}
