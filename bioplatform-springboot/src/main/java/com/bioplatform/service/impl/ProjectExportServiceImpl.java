package com.bioplatform.service.impl;

import com.bioplatform.dto.admin.FileTreeNode;
import com.bioplatform.entity.DataFile;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.PipelineExecution;
import com.bioplatform.entity.Project;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.mapper.PipelineExecutionMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.service.ProjectExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProjectExportServiceImpl implements ProjectExportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProjectMapper projectMapper;
    private final DataFileMapper dataFileMapper;
    private final PipelineMapper pipelineMapper;
    private final PipelineExecutionMapper pipelineExecutionMapper;

    public ProjectExportServiceImpl(ProjectMapper projectMapper,
                                    DataFileMapper dataFileMapper,
                                    PipelineMapper pipelineMapper,
                                    PipelineExecutionMapper pipelineExecutionMapper) {
        this.projectMapper = projectMapper;
        this.dataFileMapper = dataFileMapper;
        this.pipelineMapper = pipelineMapper;
        this.pipelineExecutionMapper = pipelineExecutionMapper;
    }

    // ========== Excel ==========

    @Override
    public ByteArrayOutputStream generateExcel(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new RuntimeException("项目不存在");

        List<DataFile> files = dataFileMapper.selectByProjectId(projectId, null);
        Pipeline param = new Pipeline();
        param.setProjectId(projectId);
        List<Pipeline> analyses = pipelineMapper.selectAll(param);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet infoSheet = wb.createSheet("项目信息");
            CellStyle headerStyle = createHeaderStyle(wb);
            int row = 0;
            row = writeRow(infoSheet, row, new String[]{"字段", "值"}, headerStyle);
            writeRow(infoSheet, row++, new String[]{"项目名称", nvl(project.getName())});
            writeRow(infoSheet, row++, new String[]{"描述", nvl(project.getDescription())});
            writeRow(infoSheet, row++, new String[]{"物种", nvl(project.getOrganism())});
            writeRow(infoSheet, row++, new String[]{"基因组版本", nvl(project.getGenomeVersion())});
            writeRow(infoSheet, row++, new String[]{"状态", project.getStatus() == 1 ? "活跃" : project.getStatus() == 2 ? "归档" : "草稿"});
            writeRow(infoSheet, row++, new String[]{"可见性", Boolean.TRUE.equals(project.getIsPrivate()) ? "私有" : "公开"});
            writeRow(infoSheet, row++, new String[]{"创建时间", project.getCreatedAt() != null ? project.getCreatedAt().format(DATE_FMT) : "-"});
            infoSheet.autoSizeColumn(0);
            infoSheet.autoSizeColumn(1);

            Sheet fileSheet = wb.createSheet("数据文件");
            row = 0;
            writeRow(fileSheet, row, new String[]{"ID", "文件名", "类型", "大小", "物种", "上传时间"}, headerStyle);
            for (DataFile f : files) {
                writeRow(fileSheet, ++row, new String[]{
                        String.valueOf(f.getId()), nvl(f.getName()), nvl(f.getFileType()),
                        formatSize(f.getFileSize()), nvl(f.getOrganism()),
                        f.getCreatedAt() != null ? f.getCreatedAt().format(DATE_FMT) : "-"
                });
            }
            for (int c = 0; c < 6; c++) fileSheet.autoSizeColumn(c);

            Sheet analysisSheet = wb.createSheet("分析记录");
            row = 0;
            writeRow(analysisSheet, row, new String[]{"ID", "名称", "类型", "分类", "描述", "创建时间"}, headerStyle);
            for (Pipeline p : analyses) {
                writeRow(analysisSheet, ++row, new String[]{
                        String.valueOf(p.getId()), nvl(p.getName()), nvl(p.getType()),
                        nvl(p.getCategory()), nvl(p.getDescription()),
                        p.getCreatedAt() != null ? p.getCreatedAt().format(DATE_FMT) : "-"
                });
            }
            for (int c = 0; c < 6; c++) analysisSheet.autoSizeColumn(c);

            wb.write(out);
            return out;
        } catch (IOException e) {
            throw new RuntimeException("生成Excel失败", e);
        }
    }

    // ========== PPT ==========

    @Override
    public ByteArrayOutputStream generatePpt(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new RuntimeException("项目不存在");

        List<DataFile> files = dataFileMapper.selectByProjectId(projectId, null);
        Pipeline param = new Pipeline();
        param.setProjectId(projectId);
        List<Pipeline> analyses = pipelineMapper.selectAll(param);

        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(new java.awt.Dimension(960, 540));

            XSLFSlideLayout titleLayout = ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE);
            XSLFSlide slide1 = ppt.createSlide(titleLayout);
            slide1.getPlaceholder(0).setText(project.getName());
            slide1.getPlaceholder(1).setText(String.format("%s | %s | %s",
                    nvl(project.getOrganism()), nvl(project.getGenomeVersion()),
                    project.getCreatedAt() != null ? project.getCreatedAt().format(DATE_FMT) : ""));

            XSLFSlideLayout contentLayout = ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide slide2 = ppt.createSlide(contentLayout);
            slide2.getPlaceholder(0).setText("项目概述");
            StringBuilder sb = new StringBuilder();
            sb.append("描述: ").append(nvl(project.getDescription())).append("\n");
            sb.append("状态: ").append(project.getStatus() == 1 ? "活跃" : project.getStatus() == 2 ? "归档" : "草稿").append("\n");
            sb.append("可见性: ").append(Boolean.TRUE.equals(project.getIsPrivate()) ? "私有" : "公开").append("\n");
            sb.append("数据文件: ").append(files.size()).append(" 个\n");
            sb.append("分析任务: ").append(analyses.size()).append(" 个");
            slide2.getPlaceholder(1).setText(sb.toString());

            if (!files.isEmpty()) {
                XSLFSlide slide3 = ppt.createSlide(contentLayout);
                slide3.getPlaceholder(0).setText("数据文件 (" + files.size() + ")");
                StringBuilder fsb = new StringBuilder();
                int limit = Math.min(files.size(), 15);
                for (int i = 0; i < limit; i++) {
                    DataFile f = files.get(i);
                    fsb.append(f.getName()).append("  (").append(formatSize(f.getFileSize())).append(")\n");
                }
                if (files.size() > 15) fsb.append("... 等共 ").append(files.size()).append(" 个文件");
                slide3.getPlaceholder(1).setText(fsb.toString());
            }

            if (!analyses.isEmpty()) {
                XSLFSlide slide4 = ppt.createSlide(contentLayout);
                slide4.getPlaceholder(0).setText("分析任务 (" + analyses.size() + ")");
                StringBuilder asb = new StringBuilder();
                int limit = Math.min(analyses.size(), 15);
                for (int i = 0; i < limit; i++) {
                    Pipeline p = analyses.get(i);
                    asb.append(p.getName()).append("  [").append(nvl(p.getType())).append("]\n");
                }
                if (analyses.size() > 15) asb.append("... 等共 ").append(analyses.size()).append(" 个任务");
                slide4.getPlaceholder(1).setText(asb.toString());
            }

            ppt.write(out);
            return out;
        } catch (IOException e) {
            throw new RuntimeException("生成PPT失败", e);
        }
    }

    // ========== 文件树（基于 PipelineExecution.outputPath） ==========

    @Override
    public List<FileTreeNode> getFileTree(Long projectId) {
        // 查询项目下所有有 outputPath 的执行记录
        List<PipelineExecution> executions = pipelineExecutionMapper.selectByProjectId(projectId, null);

        List<FileTreeNode> result = new ArrayList<>();
        for (PipelineExecution exec : executions) {
            if (exec.getOutputPath() == null || exec.getOutputPath().isBlank()) continue;

            Path outputDir = Paths.get(exec.getOutputPath());
            if (!Files.isDirectory(outputDir)) continue;

            // 获取流程名称
            String pipelineName = "执行#" + exec.getId();
            Pipeline pipeline = pipelineMapper.selectById(exec.getPipelineId());
            if (pipeline != null && pipeline.getName() != null) {
                pipelineName = pipeline.getName();
            }

            // 扫描目录，构建子节点
            List<FileTreeNode> children = scanDirectory(outputDir, outputDir);
            if (children.isEmpty()) continue;

            // 创建执行记录节点（作为顶层目录）
            FileTreeNode execNode = new FileTreeNode();
            execNode.setName(pipelineName);
            execNode.setPath(outputDir.getFileName().toString());
            execNode.setFilePath(outputDir.toString());
            execNode.setDirectory(true);
            execNode.setExecutionId(exec.getId());
            execNode.setPipelineName(pipelineName);
            execNode.setModifiedAt(exec.getFinishedAt() != null ? exec.getFinishedAt().format(DATE_FMT) : null);
            execNode.setChildren(children);
            result.add(execNode);
        }

        return result;
    }

    /**
     * 递归扫描目录，生成文件树节点列表
     */
    private List<FileTreeNode> scanDirectory(Path dir, Path rootDir) {
        List<FileTreeNode> nodes = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).forEach(entry -> {
                FileTreeNode node = new FileTreeNode();
                node.setName(entry.getFileName().toString());
                node.setPath(rootDir.relativize(entry).toString());
                node.setFilePath(entry.toString());
                node.setDirectory(Files.isDirectory(entry));

                if (Files.isDirectory(entry)) {
                    node.setChildren(scanDirectory(entry, rootDir));
                } else {
                    try {
                        node.setSize(Files.size(entry));
                    } catch (IOException e) {
                        node.setSize(0L);
                    }
                    node.setFileType(getFileExtension(entry.getFileName().toString()));
                    try {
                        node.setModifiedAt(Files.getLastModifiedTime(entry).toInstant()
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(DATE_FMT));
                    } catch (IOException e) {
                        // ignore
                    }
                }
                nodes.add(node);
            });
        } catch (IOException e) {
            log.warn("扫描目录失败: {}", dir, e);
        }
        return nodes;
    }

    // ========== 批量下载 ==========

    @Override
    public ByteArrayOutputStream batchDownload(Long projectId, List<Long> fileIds, long maxTotalBytes) {
        // fileIds 在新方案中不用了（文件没有ID），保留接口兼容
        // 实际通过 filePath 下载
        throw new RuntimeException("请使用文件路径下载");
    }

    /**
     * 按文件路径列表打包下载
     */
    public ByteArrayOutputStream batchDownloadByPaths(List<String> filePaths, long maxTotalBytes) {
        if (filePaths.isEmpty()) throw new RuntimeException("未指定文件");

        long totalSize = 0;
        List<Path> validPaths = new ArrayList<>();
        for (String fp : filePaths) {
            Path p = Paths.get(fp);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                try {
                    totalSize += Files.size(p);
                } catch (IOException e) { /* skip */ }
                validPaths.add(p);
            }
        }
        if (validPaths.isEmpty()) throw new RuntimeException("未找到有效文件");
        if (totalSize > maxTotalBytes) {
            throw new RuntimeException("文件总大小 " + formatSize(totalSize) + " 超过限制 " + formatSize(maxTotalBytes));
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Path p : validPaths) {
                zos.putNextEntry(new ZipEntry(p.getFileName().toString()));
                Files.copy(p, zos);
                zos.closeEntry();
            }
            zos.finish();
            return baos;
        } catch (IOException e) {
            throw new RuntimeException("打包下载失败", e);
        }
    }

    // ========== 工具方法 ==========

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private int writeRow(Sheet sheet, int rowNum, String[] values) {
        return writeRow(sheet, rowNum, values, null);
    }

    private int writeRow(Sheet sheet, int rowNum, String[] values, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i] != null ? values[i] : "");
            if (style != null) cell.setCellStyle(style);
        }
        return rowNum + 1;
    }

    private String nvl(String s) {
        return s != null ? s : "-";
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes == 0) return "-";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1).toLowerCase() : "";
    }
}
