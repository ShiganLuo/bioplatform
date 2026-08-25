# 流程模块重构方案

## 1. 概述

当前流程管理存在几个核心问题：
- 分类不合理，没有区分「单任务」和「多步流水线」
- 缺乏模板机制，每次新建流程都要手动填写全部字段
- config_json 字段是自由文本，前端无法解析和渲染
- 没有与 Snakemake 执行引擎对接

**目标**：基于 Omics 仓库的 `config/*.json` + `config/*.schema.json` 模式，实现模板驱动的流程创建 + schema 驱动的前端表单渲染 + Snakemake 执行。

---

## 2. 核心概念

### 2.1 流程类型

| 类型 | 说明 | 环境配置 | 示例 |
|------|------|----------|------|
| **task**（单任务） | 对应 Omics 的一个 module（如 star、samtools） | 用户可配置 singularity 容器路径 | STAR 比对、FastQC 质控 |
| **pipeline**（流水线） | 对应 Omics 的一个 subworkflow（如 RNAseq、Mutation） | 由各模块自动决定 | RNAseq 全流程、体细胞突变分析 |

### 2.2 模板与流程的关系

```
WorkflowTemplate（模板）
  ├── type: task / pipeline
  ├── configTemplate: JSON  ← 来自 Omics/config/{Name}.json
  ├── schemaJson: JSON      ← 来自 Omics/config/{Name}.schema.json
  └── snakemakePath: string ← 来自 Omics/subworkflow/{Name}.smk 或 modules/{tool}/{tool}.smk

Pipeline（用户创建的流程实例）
  ├── templateId → 关联模板
  ├── configJson: JSON  ← 用户基于模板 schema 填写的实际配置
  └── ...
```

用户新建流程时：选择模板 → 前端解析 schemaJson → 动态渲染表单 → 用户填写 → 保存为 Pipeline 实例。

---

## 3. 数据库设计

### 3.1 新增 `workflow_templates` 表

```sql
CREATE TABLE `workflow_templates` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `name`             VARCHAR(128)  NOT NULL COMMENT '模板名称，如 RNAseq、STAR',
    `description`      TEXT          DEFAULT NULL,
    `type`             VARCHAR(16)   NOT NULL COMMENT 'task / pipeline',
    `category`         VARCHAR(64)   DEFAULT NULL COMMENT '分组：转录组、变异检测、表观遗传学等',
    `config_template`  JSON          NOT NULL COMMENT '默认配置 JSON（来自 Omics/config/*.json）',
    `schema_json`      JSON          NOT NULL COMMENT '表单 schema（来自 Omics/config/*.schema.json）',
    `snakemake_path`   VARCHAR(255)  NOT NULL COMMENT '相对于 Omics 仓库的 .smk 路径',
    `icon`             VARCHAR(64)   DEFAULT NULL,
    `sort_order`       INT           DEFAULT 0,
    `enabled`          TINYINT(1)    NOT NULL DEFAULT 1,
    `created_at`       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_wt_type` (`type`),
    INDEX `idx_wt_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.2 修改 `pipelines` 表

```sql
ALTER TABLE `pipelines`
    ADD COLUMN `type`         VARCHAR(16)  NOT NULL DEFAULT 'pipeline' COMMENT 'task / pipeline' AFTER `name`,
    ADD COLUMN `template_id`  BIGINT       DEFAULT NULL COMMENT '关联模板' AFTER `type`;

ALTER TABLE `pipelines`
    ADD CONSTRAINT `fk_pipelines_template` FOREIGN KEY (`template_id`) REFERENCES `workflow_templates` (`id`);
```

### 3.3 `pipeline_executions` 表不变

已有的 `input_params`（JSON）、`output_path`、`error_log`、`status` 字段足以支撑 Snakemake 执行记录。

---

## 4. 后端设计

### 4.1 新增 WorkflowTemplate 模块

```
entity/WorkflowTemplate.java
dto/admin/AdminWorkflowTemplateDTO.java
mapper/WorkflowTemplateMapper.java
  └── WorkflowTemplateMapper.xml
service/WorkflowTemplateService.java
  └── impl/WorkflowTemplateServiceImpl.java
controller/admin/AdminWorkflowTemplateController.java
```

**API 端点**：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/admin/templates/list | 分页查询，支持 type/category 筛选 |
| GET | /api/admin/templates/{id} | 获取模板详情（含 configTemplate 和 schemaJson） |
| POST | /api/admin/templates/create | 创建模板 |
| PUT | /api/admin/templates/update | 更新模板 |
| DELETE | /api/admin/templates/{id} | 删除模板 |
| POST | /api/admin/templates/import | 从 Omics 仓库目录批量导入模板 |

### 4.2 修改 Pipeline 模块

**Pipeline 实体新增字段**：
```java
private String type;        // "task" / "pipeline"
private Long templateId;    // 关联 workflow_templates.id
```

**AdminPipelineCreateRequest 新增字段**：
```java
String type;       // "task" / "pipeline"
Long templateId;   // 模板ID
```

### 4.3 执行服务改造

**PipelineServiceImpl.executePipeline()** 流程：

1. 根据 pipelineId 查 Pipeline → 获取 templateId
2. 根据 templateId 查 WorkflowTemplate → 获取 snakemakePath、configTemplate
3. 合并配置：configTemplate + 用户的 configJson + 项目样本信息
4. 生成最终配置文件 `{project_dir}/{pipeline_name}/config.json`
5. 调用 Snakemake：
   ```
   snakemake --snakefile {Omics_ROOT}/{snakemake_path} \
             --configfile {generated_config.json} \
             --directory {work_dir} \
             --cores {threads}
   ```
6. 记录执行状态到 pipeline_executions

---

## 5. 前端设计

### 5.1 Schema → 表单渲染映射

Omics 的 schema 格式：
```json
{
  "fasta": { "type": "string", "path": "file", "nullable": true, "required": true, "description": "Reference genome FASTA" },
  "threads": { "type": "integer", "required": false, "default": 4 },
  "paired_samples": { "type": "list", "required": true },
  "skip_snp": { "type": "boolean", "required": false, "default": false }
}
```

渲染规则：

| schema type | path | 渲染组件 | 说明 |
|-------------|------|----------|------|
| string | file | el-input + 文件选择按钮 | 点击可从项目数据文件中选择 |
| string | dir | el-input + 目录选择按钮 | |
| string | prefix | el-input | 路径前缀输入 |
| string | — | el-input | 普通文本 |
| integer | — | el-input-number | 数值 |
| boolean | — | el-switch | 开关 |
| list | — | 动态标签输入（el-tag + el-input） | 可增删 |
| dict | — | 嵌套表单组（el-collapse） | 递归渲染子属性 |
| null | — | 跳过（该字段由系统自动填充） | 如 ROOT_DIR、indir、outdir |

**required** → 表单验证规则
**nullable** → 允许为空（可清空）
**description** → placeholder 文本和 tooltip

### 5.2 页面结构

#### 5.2.1 模板管理页（admin）

新增路由 `/system/templates`，在系统管理子菜单下。

功能：
- 模板列表（表格：名称、类型、分类、启用状态）
- 新建/编辑模板（表单：name、type、category、configTemplate JSON 编辑器、schemaJson JSON 编辑器、snakemakePath）
- 从 Omics 仓库导入（输入目录路径，自动扫描解析）

#### 5.2.2 流程创建改造

**当前**：表单直接填写 name、description、category、configJson

**改为**：
1. 点击「新建流程」→ 弹出类型选择：单任务 / 流水线
2. 选择类型后 → 展示该类型下的模板列表（卡片/下拉）
3. 选择模板后 → 加载 schemaJson，动态渲染配置表单
4. 用户填写表单 → 提交保存

**表单区域**：
- 基本信息：流程名称、描述
- 配置参数：由 schemaJson 动态渲染
- 环境配置（仅 task 类型）：singularity 容器路径（来自 schema 中的 `env` 部分）

#### 5.2.3 流程列表改造

表格新增列：`类型`（单任务/流水线）、`模板名称`
筛选器新增：类型筛选

### 5.3 关键组件

新增组件：
```
components/
  SchemaForm.vue          ← 核心：schema 驱动的动态表单
  SchemaFormItem.vue      ← 单个 schema 字段的渲染
  TemplateSelector.vue    ← 模板选择器（卡片式）
```

**SchemaForm.vue 设计**：
```vue
<template>
  <el-form :model="formData">
    <template v-for="(fieldSchema, fieldName) in schema" :key="fieldName">
      <!-- 跳过 type=null 的系统字段 -->
      <SchemaFormItem
        v-if="fieldSchema.type !== 'null'"
        :name="fieldName"
        :schema="fieldSchema"
        v-model="formData[fieldName]"
      />
    </template>
  </el-form>
</template>
```

**SchemaFormItem.vue 设计**：
```vue
<template>
  <el-form-item :label="name" :required="schema.required">
    <!-- dict → 嵌套 -->
    <el-collapse v-if="schema.type === 'dict'">
      <SchemaForm :schema="schema.properties" v-model="modelValue" />
    </el-collapse>

    <!-- boolean → switch -->
    <el-switch v-else-if="schema.type === 'boolean'" v-model="modelValue" />

    <!-- integer → input-number -->
    <el-input-number v-else-if="schema.type === 'integer'" v-model="modelValue" />

    <!-- list → dynamic tags -->
    <div v-else-if="schema.type === 'list'">
      <el-tag v-for="(item, i) in modelValue" :key="i" closable @close="remove(i)">
        {{ item }}
      </el-tag>
      <el-input v-model="newItem" @keyup.enter="add" size="small" />
    </div>

    <!-- string with path → input + picker -->
    <div v-else-if="schema.path">
      <el-input v-model="modelValue" :placeholder="schema.description">
        <template #append>
          <el-button @click="openPicker">选择</el-button>
        </template>
      </el-input>
    </div>

    <!-- string → plain input -->
    <el-input v-else v-model="modelValue" :placeholder="schema.description" />
  </el-form-item>
</template>
```

---

## 6. 实现步骤

### Phase 1：模板基础设施
1. 建表 `workflow_templates`，修改 `pipelines` 表
2. 实现 WorkflowTemplate 后端 CRUD
3. 实现模板管理前端页面
4. 实现从 Omics 目录批量导入模板的功能

### Phase 2：Schema 驱动表单
5. 实现 SchemaForm.vue 和 SchemaFormItem.vue
6. 改造流程创建对话框：类型选择 → 模板选择 → schema 表单
7. 改造流程列表：显示类型和模板名称

### Phase 3：Snakemake 执行
8. 实现配置合并逻辑（模板 + 用户配置 + 项目样本）
9. 实现 Snakemake 调用（subprocess）
10. 实现执行状态轮询和日志采集

---

## 7. 数据流示例

以「用户创建 RNAseq 流水线」为例：

```
1. 用户点击「新建流程」→ 选择「流水线」
2. 前端 GET /api/admin/templates/list?type=pipeline
3. 用户选择「RNAseq」模板
4. 前端 GET /api/admin/templates/1 → 返回 schemaJson
5. 前端 SchemaForm 解析 schema，渲染：
   - paired_samples: 动态标签列表 [可增删]
   - single_samples: 动态标签列表
   - env.env_dir: 文本输入
   - env.star: 文本输入（path=file）
   - Params.star.alignEndsType: 文本输入
   - genome.references.GRCm39.fasta: 文件选择
   - ...
6. 用户填写完 → POST /api/admin/pipelines/create
   body: { name, type:"pipeline", templateId:1, configJson:{填写的配置} }
7. 执行时：
   - 读取 template.configTemplate
   - 深度合并 pipeline.configJson
   - 注入项目样本信息到 paired_samples/single_samples
   - 写入 config.json
   - 调用 snakemake --snakefile subworkflow/RNAseq.smk --configfile config.json
```

---

## 8. 待确认问题

1. Omics 仓库部署路径：服务器上 Omics 仓库的绝对路径是什么？需要在系统配置中设定。
2. Singularity 容器路径：env 中的 .sif 路径是服务器上的实际路径，是否需要在平台中管理容器镜像？
3. 模板导入方式：手动创建 vs 自动扫描 Omics/config/ 目录？
4. 执行方式：同步执行（等待完成）vs 异步执行（后台运行 + 状态轮询）？建议异步。
5. 样本信息来源：从项目的 data_files 表自动推断？还是用户手动填写 paired_samples/single_samples？
