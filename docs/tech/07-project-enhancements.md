# 项目管理增强设计

本文档汇总项目详情页三大增强功能的设计方案：父子项目层级、结果导出、Meta 编辑器。

---

## 一、父子项目（项目层级）

### 1.1 需求

项目支持父子层级关系：若干个小项目组成一个大项目。例如：
- 大项目："2026年肿瘤基因组研究"
  - 子项目：cfDNA_aging
  - 子项目：WGS_tumor_normal
  - 子项目：RNAseq_immunotherapy

### 1.2 数据库

```sql
ALTER TABLE projects ADD COLUMN parent_id BIGINT NULL COMMENT '父项目ID，NULL表示顶级项目' AFTER id;
ALTER TABLE projects ADD INDEX idx_parent_id (parent_id);
```

### 1.3 后端改动

Project 实体新增字段：
```java
private Long parentId;  // 父项目ID，null=顶级项目
```

查询接口：
- 列表接口返回 `parentName` 字段（联表查父项目名称）
- 新增 `GET /api/admin/projects/tree` 返回树形结构（可选，二期）

DTO：`AdminProjectCreateRequest` / `AdminProjectUpdateRequest` 增加 `parentId` 字段。

### 1.4 前端改动

**ProjectView.vue（项目列表）**：
- 表格新增列：在"项目名称"后加"所属父项目"列
- 新建/编辑弹窗：新增"所属父项目"下拉选择器
  - 可选，不选则为顶级项目
  - 不能选自己作为父项目（编辑时）
  - 下拉列表排除当前项目及其子项目（防止循环）

**ProjectDetailView.vue（项目详情）**：
- 显示父项目链接（如果有）
- 显示子项目列表（如果有）

### 1.5 约束

- 删除父项目时：子项目的 `parent_id` 置为 NULL（级联解绑，不级联删除）
- 最多 2 层深度（父→子），不支持更深嵌套
- 子项目继承父项目的可见性（isPrivate）

### 1.6 变更清单

| 文件 | 改动 |
|------|------|
| projects 表 | 新增 parent_id 列 + 索引 |
| Project.java | 新增 parentId 字段 |
| ProjectMapper.xml | 查询联表 parentName |
| AdminProjectController.java | DTO 增加 parentId |
| ProjectView.vue | 表格+弹窗增加父项目 |
| ProjectDetailView.vue | 显示父子关系 |

---

## 二、项目结果导出

### 2.1 需求

在项目详情页添加导出功能：
1. **标准导出**：生成 PPT 报告 + Excel 数据表
2. **通用下载**：文件夹式浏览，支持下载项目下的任意文件

### 2.2 整体方案

在 ProjectDetailView.vue 中新增一个独立的「项目文件」卡片，放在分析列表卡片之后：

```
┌──────────────────────────────────────────────────┐
│ 项目文件    [下载PPT] [下载Excel] [全部下载(zip)] │
├──────────────────────────────────────────────────┤
│ ☐  📁 output/                                    │
│   ☐  📄 report.xlsx          1.2 MB   xlsx       │
│   ☐  📄 heatmap.png          340 KB   png        │
│ ☐  📁 raw_data/                                  │
│   ☐  📄 sample_meta.tsv      12 KB    tsv        │
│ ☐  📄 pipeline.log           89 KB    log        │
│                                                    │
│                              [下载选中文件]        │
└──────────────────────────────────────────────────┘
```

卡片主体：文件浏览器（el-table），显示项目下所有文件，支持目录层级展开、勾选。

### 2.3 标准导出（PPT + Excel）

前端调用后端接口，后端生成文件返回 blob，前端触发浏览器下载。

- `GET /api/admin/projects/{id}/export/ppt` → 返回 .pptx blob
- `GET /api/admin/projects/{id}/export/excel` → 返回 .xlsx blob

**PPT 内容**：
- 封面：项目名称、物种、基因组版本、创建时间
- 项目概述：描述、状态、可见性
- 数据文件列表：文件名、类型、大小、上传时间
- 分析列表：名称、类型、分类、状态、创建时间

**Excel 内容**：
- Sheet1 项目信息：项目元数据
- Sheet2 数据文件：文件列表
- Sheet3 分析记录：分析/流程列表

### 2.4 通用下载（文件浏览器）

弹出 el-dialog，以树形/列表展示项目下的文件目录。支持：
- 按目录层级浏览（如果文件有路径结构）
- 单文件下载
- 批量勾选下载（打包为 zip）

前端调用：
- `GET /api/admin/projects/{id}/files/tree` → 返回文件树结构
- `GET /api/admin/datafiles/{id}/download` → 已有接口，单文件下载
- `POST /api/admin/projects/{id}/files/batch-download` → 批量打包下载

### 2.5 后端改动

**新增依赖**（pom.xml）：`poi-ooxml` 5.2.5（同时包含 xlsx 和 pptx 支持）

**新增接口**（AdminProjectController）：

```java
@GetMapping("/{id}/export/excel")
public void exportExcel(@PathVariable Long id, HttpServletResponse response)

@GetMapping("/{id}/export/ppt")
public void exportPpt(@PathVariable Long id, HttpServletResponse response)

@GetMapping("/{id}/files/tree")
public ApiResponse<List<FileTreeNode>> getFileTree(@PathVariable Long id)

@PostMapping("/{id}/files/batch-download")
public void batchDownload(@PathVariable Long id, @RequestBody List<String> filePaths, HttpServletResponse response)
```

**新增服务** `ProjectExportService`：
- `generateExcel(projectId)` → ByteArrayOutputStream
- `generatePpt(projectId)` → ByteArrayOutputStream
- `getFileTree(projectId)` → List<FileTreeNode>

**FileTreeNode DTO**：
```java
public class FileTreeNode {
    Long id;
    String name;
    String filePath;
    boolean directory;
    Long size;
    String fileType;
    List<FileTreeNode> children;
}
```

### 2.6 交互设计

1. 点击「生成PPT报告」→ loading → 浏览器下载 .pptx
2. 点击「生成Excel数据表」→ loading → 浏览器下载 .xlsx
3. 点击「下载文件」→ 弹出文件浏览器对话框：
   - 左侧目录树（如果有层级）
   - 右侧文件列表（勾选框 + 文件名 + 大小 + 类型）
   - 底部「下载选中」按钮（单个直接下载，多个打包 zip）

### 2.7 变更清单

```
新增文件：
bioplatform-springboot/src/main/java/com/bioplatform/service/ProjectExportService.java
bioplatform-springboot/src/main/java/com/bioplatform/service/impl/ProjectExportServiceImpl.java
bioplatform-springboot/src/main/java/com/bioplatform/dto/admin/FileTreeNode.java

修改文件：
bioplatform-springboot/pom.xml（添加 poi-ooxml 依赖）
bioplatform-springboot/src/main/java/com/bioplatform/controller/admin/AdminProjectController.java（添加导出接口）
bioplatform-vue3/bioplatform-admin/src/api/projectApi.ts（添加导出API）
bioplatform-vue3/bioplatform-admin/src/views/project/ProjectDetailView.vue（添加导出UI）
```

---

## 三、Meta 信息编辑器

### 3.1 需求

在项目详情页添加 Meta 编辑器，用于编辑 Omics 流程的样本元信息。支持表格编辑和 TSV 文本两种模式，可互相切换。Meta 是整个流程的起点，由 MetaUtil.py 解析。

### 3.2 Meta 格式

TSV/CSV，列由模式自动检测：

| 模式 | 必需列 | 可选列 |
|------|--------|--------|
| FASTQ | sample_id, fastq_1, fastq_2 | data_id, design, group, organism, workflow |
| PacBio | sample_id, bam, pbi | design, group, organism |
| MS | sample_id, ms_file | organism |
| scRNA-seq | sample_id, fastq_dir, sample_prefix | design, group, organism |

design 格式：`ctrl_TAG` / `ctr_TAG` / `exp_TAG`，TAG 用 `_` 分隔。ctrl 和 exp 通过共享 TAG 配对，如 `ctrl_WT` + `exp_WT` → 配对。

### 3.3 存储方案

单独建表 `sample_meta`，一个项目可以有多份 meta（不同实验/批次）：

```sql
CREATE TABLE sample_meta (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    project_id  BIGINT       NOT NULL COMMENT '所属项目',
    name        VARCHAR(128) NOT NULL COMMENT 'meta名称，如 "RNA-seq WT vs KO"',
    meta_mode   VARCHAR(32)  DEFAULT 'fastq' COMMENT '模式: fastq/pacbio/ms/scrnaseq',
    meta_content TEXT         NOT NULL COMMENT 'TSV内容',
    description VARCHAR(512) DEFAULT NULL,
    created_by  BIGINT       DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

一个项目可以有多条 meta 记录，创建分析时选择使用哪条。

### 3.4 后端改动

**Entity** 新增 `SampleMeta.java`：
```java
@Data
public class SampleMeta {
    private Long id;
    private Long projectId;
    private String name;
    private String metaMode;    // fastq/pacbio/ms/scrnaseq
    private String metaContent; // TSV内容
    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**Mapper** 新增 `SampleMetaMapper.java` + `SampleMetaMapper.xml`：
- insert / selectById / selectByProjectId / updateById / deleteById

**Controller** 新增 `AdminSampleMetaController.java`（`/api/admin/sample-meta`）：
- `GET /list?projectId=` → 列表
- `GET /{id}` → 详情
- `POST /create` → 创建
- `PUT /update` → 更新
- `DELETE /{id}` → 删除

**Service** 新增 `SampleMetaService.java` + `SampleMetaServiceImpl.java`

### 3.5 前端改动

**API** 新增 `sampleMetaApi.ts`：
```typescript
export interface SampleMeta {
  id: number
  projectId: number
  name: string
  metaMode: string
  metaContent: string
  description: string
  createdAt: string
}

export function listSampleMeta(projectId: number) { ... }
export function getSampleMeta(id: number) { ... }
export function createSampleMeta(data: Partial<SampleMeta>) { ... }
export function updateSampleMeta(id: number, data: Partial<SampleMeta>) { ... }
export function deleteSampleMeta(id: number) { ... }
```

**UI** 在 ProjectDetailView.vue 中，「数据文件」和「分析列表」之间新增「样本信息」卡片：

```
┌──────────────────────────────────────────────────┐
│ 样本信息                    [+ 新建] [导入TSV]    │
├──────────────────────────────────────────────────┤
│ meta列表（el-table）:                              │
│ ┌────┬──────────────┬───────┬────────┬────────┐  │
│ │ ID │ 名称          │ 模式   │ 样本数  │ 操作   │  │
│ ├────┼──────────────┼───────┼────────┼────────┤  │
│ │ 1  │ RNA-seq WT/KO│ fastq │ 6      │编辑 删除│  │
│ └────┴──────────────┴───────┴────────┴────────┘  │
└──────────────────────────────────────────────────┘
```

点击「编辑」弹出 meta 编辑对话框，支持两种模式切换：

**表格模式**：
```
┌──────────────────────────────────────────────┐
│ sample_id | design  | fastq_1 | fastq_2 | ..│
│ Sample1   | ctrl_WT | /data/.. | /data/.. │
│ Sample2   | exp_WT  | /data/.. | /data/.. │
│ [+ 添加行]  [添加列]                         │
└──────────────────────────────────────────────┘
```

**TSV 模式**：
```
┌──────────────────────────────────────────────┐
│ sample_id\tdesign\tfastq_1\tfastq_2         │
│ Sample1\tctrl_WT\t/data/S1_R1.fq.gz\t...   │
└──────────────────────────────────────────────┘
```

### 3.6 变更清单

```
新增文件：
bioplatform-springboot/src/main/java/com/bioplatform/entity/SampleMeta.java
bioplatform-springboot/src/main/java/com/bioplatform/mapper/SampleMetaMapper.java
bioplatform-springboot/src/main/resources/mapper/SampleMetaMapper.xml
bioplatform-springboot/src/main/java/com/bioplatform/service/SampleMetaService.java
bioplatform-springboot/src/main/java/com/bioplatform/service/impl/SampleMetaServiceImpl.java
bioplatform-springboot/src/main/java/com/bioplatform/controller/admin/AdminSampleMetaController.java
bioplatform-vue3/bioplatform-admin/src/api/sampleMetaApi.ts

修改文件：
bioplatform-vue3/bioplatform-admin/src/views/project/ProjectDetailView.vue
database/bioplatform.sql
```
