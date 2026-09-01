# 附属项目（项目层级）设计方案

## 需求

项目支持父子层级关系：若干个小项目组成一个大项目。例如：
- 大项目："2026年肿瘤基因组研究"
  - 子项目：cfDNA_aging
  - 子项目：WGS_tumor_normal
  - 子项目：RNAseq_immunotherapy

## 数据库

```sql
ALTER TABLE projects ADD COLUMN parent_id BIGINT NULL COMMENT '父项目ID，NULL表示顶级项目' AFTER id;
ALTER TABLE projects ADD INDEX idx_parent_id (parent_id);
```

## 后端改动

### Project 实体
```java
private Long parentId;  // 父项目ID，null=顶级项目
```

### 查询接口
- 列表接口返回 `parentName` 字段（联表查父项目名称）
- 新增 `GET /api/admin/projects/tree` 返回树形结构（可选，二期）

### DTO
- AdminProjectCreateRequest / AdminProjectUpdateRequest 增加 `parentId` 字段

## 前端改动

### ProjectView.vue（项目列表）

**表格新增列**：在"项目名称"后加"所属父项目"列

**新建/编辑弹窗**：新增"所属父项目"下拉选择器
- 可选，不选则为顶级项目
- 不能选自己作为父项目（编辑时）
- 下拉列表排除当前项目及其子项目（防止循环）

### ProjectDetailView.vue（项目详情）

- 显示父项目链接（如果有）
- 显示子项目列表（如果有）

## 约束

- 删除父项目时：子项目的 `parent_id` 置为 NULL（级联解绑，不级联删除）
- 最多2层深度（父→子），不支持更深嵌套
- 子项目继承父项目的可见性（isPrivate）

## 变更清单

| 文件 | 改动 |
|------|------|
| projects 表 | 新增 parent_id 列 + 索引 |
| Project.java | 新增 parentId 字段 |
| ProjectMapper.xml | 查询联表 parentName |
| AdminProjectController.java | DTO 增加 parentId |
| ProjectView.vue | 表格+弹窗增加父项目 |
| ProjectDetailView.vue | 显示父子关系 |
