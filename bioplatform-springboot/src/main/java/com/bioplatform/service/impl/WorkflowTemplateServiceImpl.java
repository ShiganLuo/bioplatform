package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.common.util.HostPathUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        template.setCommandTemplate(request.commandTemplate());
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
        template.setCommandTemplate(request.commandTemplate());
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

    /** 工作流 meta 规范目录(Omics 仓库内;列与示例行因流程而异,如 RNAseq 带 organism/contaminated_organism,CLIP 不带) */
    @org.springframework.beans.factory.annotation.Value("${bioplatform.omics.root:/data/16T/luosg/workflow/Omics}")
    private String omicsRootProp;

    @Override
    public MetaTemplate getMetaTemplate(Long id) {
        WorkflowTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        if (!"pipeline".equals(tpl.getType())) {
            return new MetaTemplate(false, List.of(), List.of(), "");
        }
        try {
            Path f = com.bioplatform.common.util.HostPathUtil.resolveExisting(
                    omicsRootProp + "/assests/test/meta_" + tpl.getName() + ".tsv");
            List<String> lines = java.nio.file.Files.readAllLines(f, java.nio.charset.StandardCharsets.UTF_8)
                    .stream().filter(l -> !l.isBlank()).limit(3).toList();
            if (lines.isEmpty()) {
                return new MetaTemplate(false, List.of(), List.of(), "");
            }
            List<String> columns = List.of(lines.get(0).split("\t", -1));
            List<String> examples = List.copyOf(lines.subList(1, lines.size()));
            return new MetaTemplate(true, columns, examples, String.join("\n", lines));
        } catch (Exception e) {
            return new MetaTemplate(false, List.of(), List.of(), "");
        }
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
        Path root = resolvePath(omicsDir);

        // 单个模块 json 文件（如 .../modules/msi/msi.json）
        if (Files.isRegularFile(root)) {
            String fileName = root.getFileName().toString();
            if (!fileName.endsWith(".json") || fileName.endsWith(".schema.json") || isInfraSchema(fileName)) {
                throw new IllegalArgumentException("不支持的文件（需为模块 xxx.json）: " + root);
            }
            String base = fileName.substring(0, fileName.length() - ".json".length());
            if (!Files.exists(root.resolveSibling(base + ".schema.json"))) {
                throw new IllegalArgumentException("缺少同名 schema 文件: " + root.resolveSibling(base + ".schema.json"));
            }
            Path scanRoot = guessModuleScanRoot(root, base);
            try {
                return importModuleFile(root, scanRoot, findOmicsRoot(root.getParent()));
            } catch (IOException e) {
                throw new RuntimeException("读取模块 json 失败: " + e.getMessage(), e);
            }
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("路径不存在: " + omicsDir + "（已尝试容器内映射路径 /host" + omicsDir + "，如在服务器上请直接填宿主机路径）");
        }

        int count = 0;
        Path configDir = root.resolve("config");
        Path modulesDir = root.resolve("modules");
        Path omicsRoot = findOmicsRoot(root);

        if (Files.isDirectory(configDir)) {
            count += importConfigDir(root, configDir);
        }
        if (Files.isDirectory(modulesDir)) {
            count += importModuleDir(modulesDir, omicsRoot != null ? omicsRoot : root);
        } else if (!Files.isDirectory(configDir)) {
            // 传入的本身是模块目录（可含变体子目录），递归解析其中的 xxx.json + xxx.schema.json
            count += importModuleDir(root, omicsRoot != null ? omicsRoot : root.getParent());
        }

        if (count == 0) {
            throw new IllegalArgumentException("未在 " + root + " 下发现可导入模板（config/*.json 或 **/xxx.json + 同名 xxx.schema.json）");
        }
        return count;
    }

    /**
     * 导入 Omics 仓库 config/ 目录下的工作流配置（原有行为）
     */
    private int importConfigDir(Path omicsDir, Path configDir) {
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
                    Path subworkflowFile = omicsDir.resolve("subworkflow").resolve(workflowName + ".smk");
                    String type = Files.exists(subworkflowFile) ? "pipeline" : "task";

                    // 确定 snakemake 路径
                    String snakemakePath;
                    if (type.equals("pipeline")) {
                        snakemakePath = "subworkflow/" + workflowName + ".smk";
                    } else {
                        snakemakePath = "modules/" + workflowName + "/" + workflowName + ".smk";
                    }

                    upsertTemplate(workflowName, "从 Omics 仓库导入: " + workflowName, type,
                            guessCategory(workflowName), configJson, schemaJson, snakemakePath);
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
     * 递归导入模块目录下的 xxx.json + xxx.schema.json 对（支持变体子目录，如 polygenomes/、tumor-only/）
     */
    private int importModuleDir(Path scanRoot, Path omicsRoot) {
        int count = 0;
        List<Path> jsonFiles;
        try (Stream<Path> walk = Files.walk(scanRoot)) {
            jsonFiles = walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.getFileName().toString();
                        return s.endsWith(".json") && !s.endsWith(".schema.json");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("读取模块目录失败: " + e.getMessage(), e);
        }

        for (Path jsonFile : jsonFiles) {
            String fileName = jsonFile.getFileName().toString();
            if (isInfraSchema(fileName)) {
                continue;
            }
            String base = fileName.substring(0, fileName.length() - ".json".length());
            if (!Files.exists(jsonFile.resolveSibling(base + ".schema.json"))) {
                log.warn("跳过 {}，无对应 schema 文件", jsonFile.getFileName());
                continue;
            }
            try {
                count += importModuleFile(jsonFile, scanRoot, omicsRoot);
            } catch (Exception e) {
                log.error("导入模块 {} 失败，跳过: {}", jsonFile, e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * 导入单个模块 json（xxx.json + 同目录 xxx.schema.json + xxx.smk）
     */
    private int importModuleFile(Path jsonFile, Path scanRoot, Path omicsRoot) throws IOException {
        if (scanRoot != null && !jsonFile.startsWith(scanRoot)) {
            scanRoot = jsonFile.getParent(); // 防御：scanRoot 与文件位置不符时退回父目录，避免 ../ 变体名
        }
        String fileName = jsonFile.getFileName().toString();
        String base = fileName.substring(0, fileName.length() - ".json".length());
        Path schemaFile = jsonFile.resolveSibling(base + ".schema.json");
        String configJson = Files.readString(jsonFile);
        String schemaJson = Files.readString(schemaFile);

        // 模板名 = 基名 + 变体子目录后缀（如 trgt-polygenomes、msisensor_pro-tumor-only），
        // 相对 scanRoot 的父目录链中首个与基名同名(忽略大小写与 -/_)的目录视为模块本体目录，不进后缀
        String name = base;
        if (scanRoot != null) {
            Path relParent = scanRoot.relativize(jsonFile.getParent());
            List<String> variants = new ArrayList<>();
            for (Path part : relParent) {
                String s = part.toString();
                if (variants.isEmpty() && normalizeName(s).equals(normalizeName(base))) {
                    continue;
                }
                variants.add(s);
            }
            if (!variants.isEmpty()) {
                name = base + "-" + String.join("-", variants);
            }
        }

        // snakemake 路径：Omics 仓库内为相对仓库根路径，仓库外退回绝对路径
        Path smkFile = jsonFile.resolveSibling(base + ".smk");
        String snakemakePath;
        if (omicsRoot != null && smkFile.startsWith(omicsRoot)) {
            snakemakePath = omicsRoot.relativize(smkFile).toString().replace('\\', '/');
        } else {
            snakemakePath = smkFile.toString();
        }
        if (!Files.exists(smkFile)) {
            log.warn("模块 {} 无同名 .smk 文件，snakemakePath 按约定记录: {}", name, snakemakePath);
        }

        String relDesc = (scanRoot != null && jsonFile.startsWith(scanRoot))
                ? scanRoot.relativize(jsonFile).toString().replace('\\', '/')
                : jsonFile.toString();
        upsertTemplate(name, "从 Omics 仓库导入: " + relDesc, "task",
                guessCategory(base), configJson, schemaJson, snakemakePath);
        log.info("导入模块模板: {} (snakemakePath={})", name, snakemakePath);
        return 1;
    }

    /**
     * 同名模板存在则更新配置，否则新建
     */
    private void upsertTemplate(String name, String description, String type, String category,
                                String configJson, String schemaJson, String snakemakePath) {
        WorkflowTemplate existing = new WorkflowTemplate();
        existing.setName(name);
        List<WorkflowTemplate> dupes = templateMapper.selectAll(existing);
        if (!dupes.isEmpty()) {
            log.info("模板 {} 已存在，更新配置", name);
            WorkflowTemplate dup = dupes.get(0);
            dup.setConfigTemplate(configJson);
            dup.setSchemaJson(schemaJson);
            dup.setSnakemakePath(snakemakePath);
            dup.setType(type);
            templateMapper.updateById(dup);
        } else {
            WorkflowTemplate template = new WorkflowTemplate();
            template.setName(name);
            template.setDescription(description);
            template.setType(type);
            template.setCategory(category);
            template.setConfigTemplate(configJson);
            template.setSchemaJson(schemaJson);
            template.setSnakemakePath(snakemakePath);
            template.setSortOrder(0);
            template.setEnabled(true);
            templateMapper.insert(template);
        }
    }

    /**
     * 路径解析：容器内跑时宿主机路径自动映射 /host 前缀（统一走 HostPathUtil）
     */
    private Path resolvePath(String raw) {
        Path p = HostPathUtil.resolveExisting(raw);
        if (!p.toString().equals(raw)) {
            log.info("路径 {} 在容器内映射为 {}", raw, p);
        }
        return p;
    }

    /**
     * 向上查找 Omics 仓库根（同时含 modules/ 与 config/，或目录名为 Omics）
     */
    private Path findOmicsRoot(Path start) {
        Path p = start;
        while (p != null) {
            if (Files.isDirectory(p.resolve("modules")) && Files.isDirectory(p.resolve("config"))) {
                return p;
            }
            if (p.getFileName() != null && "Omics".equals(p.getFileName().toString())) {
                return p;
            }
            p = p.getParent();
        }
        return null;
    }

    /**
     * 单文件导入时推断变体命名的 scanRoot：文件位于 <root>/modules/... 下时取 modules 目录，否则取父目录
     */
    private Path guessModuleScanRoot(Path jsonFile, String base) {
        Path omicsRoot = findOmicsRoot(jsonFile.getParent());
        if (omicsRoot != null && Files.isDirectory(omicsRoot.resolve("modules"))) {
            return omicsRoot.resolve("modules");
        }
        return jsonFile.getParent();
    }

    private String normalizeName(String s) {
        return s.replace('_', '-').toLowerCase();
    }

    /**
     * 通用 schema 基建文件（schema.json / schema.schema.json），不是模块配置，跳过
     */
    private boolean isInfraSchema(String fileName) {
        return fileName.equals("schema.json") || fileName.equals("schema.schema.json");
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
        if (lower.contains("coculture")) return "共培养";
        return "其他";
    }
}
