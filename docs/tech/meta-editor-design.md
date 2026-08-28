# 项目 Meta 信息编辑器设计方案

## 需求

在项目详情页添加 Meta 编辑器，用于编辑 Omics 流程的样本元信息。
支持表格编辑和 TSV 文本两种模式，可互相切换。
Meta 是整个流程的起点，由 MetaUtil.py 解析。

## Meta 格式

TSV/CSV，列由模式自动检测：

| 模式 | 必需列 | 可选列 |
|------|--------|--------|
| FASTQ | sample_id, fastq_1, fastq_2 | data_id, design, group, organism, workflow |
| PacBio | sample_id, bam, pbi | design, group, organism |
| MS | sample_id, ms_file | organism |
| scRNA-seq | sample_id, fastq_dir, sample_prefix | design, group, organism |

design 格式：`ctrl_TAG` / `ctr_TAG` / `exp_TAG`，TAG 用 `_` 分隔。
ctrl 和 exp 通过共享 TAG 配对，如 `ctrl_WT` + `exp_WT` → 配对。

## 存储方案

单独建表 `sample_meta`，一个项目可以有多份 meta（不同实验/批次）。

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

## 后端改动

### Entity

新增 `SampleMeta.java`：
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

### Mapper

新增 `SampleMetaMapper.java` + `SampleMetaMapper.xml`：
- insert / selectById / selectByProjectId / updateById / deleteById

### Controller

新增 `AdminSampleMetaController.java`（`/api/admin/sample-meta`）：
- `GET /list?projectId=` → 列表
- `GET /{id}` → 详情
- `POST /create` → 创建
- `PUT /update` → 更新
- `DELETE /{id}` → 删除

### Service

新增 `SampleMetaService.java` + `SampleMetaServiceImpl.java`

## 前端改动

### API

新增 `sampleMetaApi.ts`：
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

### UI

在 ProjectDetailView.vue 中，「数据文件」和「分析列表」之间新增「样本信息」卡片：

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

点击「编辑」弹出 meta 编辑对话框：

```
┌──────────────────────────────────────────────────┐
│ 编辑 Meta: RNA-seq WT/KO                  [保存] │
├──────────────────────────────────────────────────┤
│ 模式: [FASTQ ▼]    名称: [RNA-seq WT/KO       ] │
│                                                    │
│ [表格编辑] [TSV编辑]                               │
│                                                    │
│ 表格模式:                                          │
│ ┌──────────────────────────────────────────────┐  │
│ │ sample_id | design  | fastq_1 | fastq_2 | ..│  │
│ │ Sample1   | ctrl_WT | /data/.. | /data/.. │  │
│ │ Sample2   | exp_WT  | /data/.. | /data/.. │  │
│ │ [+ 添加行]  [添加列]                         │  │
│ └──────────────────────────────────────────────┘  │
│                                                    │
│ TSV模式:                                           │
│ ┌──────────────────────────────────────────────┐  │
│ │ sample_id\tdesign\tfastq_1\tfastq_2         │  │
│ │ Sample1\tctrl_WT\t/data/S1_R1.fq.gz\t...   │  │
│ └──────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

## 文件结构

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
