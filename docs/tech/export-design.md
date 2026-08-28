# 项目结果导出功能设计方案

## 需求

在项目详情页（ProjectDetailView）添加导出功能：
1. **标准导出**：生成 PPT 报告 + Excel 数据表
2. **通用下载**：文件夹式浏览，支持下载项目下的任意文件

## 整体方案

### 前端改动

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
右上角三个按钮：
1. **下载PPT** → loading → 浏览器下载 .pptx
2. **下载Excel** → loading → 浏览器下载 .xlsx
3. **全部下载(zip)** → 检查总大小 → 超过限制提示 → 后端打包下载

#### 1. 标准导出（PPT + Excel）

前端调用后端接口，后端生成文件返回 blob，前端触发浏览器下载。

- `GET /api/admin/projects/{id}/export/ppt` → 返回 .pptx blob
- `GET /api/admin/projects/{id}/export/excel` → 返回 .xlsx blob

PPT 内容：
- 封面：项目名称、物种、基因组版本、创建时间
- 项目概述：描述、状态、可见性
- 数据文件列表：文件名、类型、大小、上传时间
- 分析列表：名称、类型、分类、状态、创建时间

Excel 内容：
- Sheet1 项目信息：项目元数据
- Sheet2 数据文件：文件列表
- Sheet3 分析记录：分析/流程列表

#### 2. 通用下载（文件浏览器）

弹出 el-dialog，以树形/列表展示项目下的文件目录。支持：
- 按目录层级浏览（如果文件有路径结构）
- 单文件下载
- 批量勾选下载（打包为 zip）

前端调用：
- `GET /api/admin/projects/{id}/files/tree` → 返回文件树结构
- `GET /api/admin/datafiles/{id}/download` → 已有接口，单文件下载
- `POST /api/admin/projects/{id}/files/batch-download` → 批量打包下载

### 后端改动

#### 新增依赖（pom.xml）

```xml
<!-- Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
<!-- PPT -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

poi-ooxml 同时包含 xlsx 和 pptx 支持。

#### 新增控制器

在 `AdminProjectController` 中添加3个接口：

```java
@GetMapping("/{id}/export/excel")
public void exportExcel(@PathVariable Long id, HttpServletResponse response)

@GetMapping("/{id}/export/ppt")
public void exportPpt(@PathVariable Long id, HttpServletResponse response)

@GetMapping("/{id}/files/tree")
public ApiResponse<List<FileTreeNode>> getFileTree(@PathVariable Long id)

@PostMapping("/{id}/files/batch-download")
public void batchDownload(@PathVariable Long id, @RequestBody List<Long> fileIds, HttpServletResponse response)
```

#### 新增服务

`ProjectExportService`：
- `generateExcel(projectId)` → ByteArrayOutputStream
- `generatePpt(projectId)` → ByteArrayOutputStream
- `getFileTree(projectId)` → List<FileTreeNode>

`FileTreeNode` DTO：
```java
public class FileTreeNode {
    Long id;
    String name;
    String path;
    boolean directory;
    Long size;
    String fileType;
    List<FileTreeNode> children;
}
```

## 文件结构

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

## 交互设计

1. 点击「生成PPT报告」→ loading → 浏览器下载 .pptx
2. 点击「生成Excel数据表」→ loading → 浏览器下载 .xlsx
3. 点击「下载文件」→ 弹出文件浏览器对话框：
   - 左侧目录树（如果有层级）
   - 右侧文件列表（勾选框 + 文件名 + 大小 + 类型）
   - 底部「下载选中」按钮（单个直接下载，多个打包zip）
