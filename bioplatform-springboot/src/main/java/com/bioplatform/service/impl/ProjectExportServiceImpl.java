package com.bioplatform.service.impl;

import com.bioplatform.dto.admin.FileTreeNode;
import com.bioplatform.entity.DataFile;
import com.bioplatform.entity.Pipeline;
import com.bioplatform.entity.Project;
import com.bioplatform.mapper.DataFileMapper;
import com.bioplatform.mapper.PipelineMapper;
import com.bioplatform.mapper.ProjectMapper;
import com.bioplatform.service.ProjectExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProjectExportServiceImpl implements ProjectExportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportServiceImpl.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProjectMapper projectMapper;
    private final DataFileMapper dataFileMapper;
    private final PipelineMapper pipelineMapper;

    public ProjectExportServiceImpl(ProjectMapper projectMapper,
                                    DataFileMapper dataFileMapper,
                                    PipelineMapper pipelineMapper) {
        this.projectMapper = projectMapper;
        this.dataFileMapper = dataFileMapper;
        this.pipelineMapper = pipelineMapper;
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
            // Sheet1: 项目信息
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

            // Sheet2: 数据文件
            Sheet fileSheet = wb.createSheet("数据文件");
            row = 0;
            writeRow(fileSheet, row, new String[]{"ID", "文件名", "类型", "大小", "物种", "上传时间"}, headerStyle);
            for (DataFile f : files) {
                writeRow(fileSheet, ++row, new String[]{
                        String.valueOf(f.getId()),
                        nvl(f.getName()),
                        nvl(f.getFileType()),
                        formatSize(f.getFileSize()),
                        nvl(f.getOrganism()),
                        f.getCreatedAt() != null ? f.getCreatedAt().format(DATE_FMT) : "-"
                });
            }
            for (int c = 0; c < 6; c++) fileSheet.autoSizeColumn(c);

            // Sheet3: 分析记录
            Sheet analysisSheet = wb.createSheet("分析记录");
            row = 0;
            writeRow(analysisSheet, row, new String[]{"ID", "名称", "类型", "分类", "描述", "创建时间"}, headerStyle);
            for (Pipeline p : analyses) {
                writeRow(analysisSheet, ++row, new String[]{
                        String.valueOf(p.getId()),
                        nvl(p.getName()),
                        nvl(p.getType()),
                        nvl(p.getCategory()),
                        nvl(p.getDescription()),
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

            // Slide 1: 封面
            XSLFSlideLayout titleLayout = ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE);
            XSLFSlide slide1 = ppt.createSlide(titleLayout);
            XSLFTextShape titleShape = slide1.getPlaceholder(0);
            titleShape.setText(project.getName());
            XSLFTextShape subtitleShape = slide1.getPlaceholder(1);
            subtitleShape.setText(String.format("%s | %s | %s",
                    nvl(project.getOrganism()), nvl(project.getGenomeVersion()),
                    project.getCreatedAt() != null ? project.getCreatedAt().format(DATE_FMT) : ""));

            // Slide 2: 项目概述
            XSLFSlideLayout contentLayout = ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
            XSLFSlide slide2 = ppt.createSlide(contentLayout);
            slide2.getPlaceholder(0).setText("项目概述");
            XSLFTextShape body2 = slide2.getPlaceholder(1);
            StringBuilder sb = new StringBuilder();
            sb.append("描述: ").append(nvl(project.getDescription())).append("\n");
            sb.append("状态: ").append(project.getStatus() == 1 ? "活跃" : project.getStatus() == 2 ? "归档" : "草稿").append("\n");
            sb.append("可见性: ").append(Boolean.TRUE.equals(project.getIsPrivate()) ? "私有" : "公开").append("\n");
            sb.append("数据文件: ").append(files.size()).append(" 个\n");
            sb.append("分析任务: ").append(analyses.size()).append(" 个");
            body2.setText(sb.toString());

            // Slide 3: 数据文件列表
            if (!files.isEmpty()) {
                XSLFSlide slide3 = ppt.createSlide(contentLayout);
                slide3.getPlaceholder(0).setText("数据文件 (" + files.size() + ")");
                XSLFTextShape body3 = slide3.getPlaceholder(1);
                StringBuilder fsb = new StringBuilder();
                int limit = Math.min(files.size(), 15);
                for (int i = 0; i < limit; i++) {
                    DataFile f = files.get(i);
                    fsb.append(f.getName()).append("  (").append(formatSize(f.getFileSize())).append(")\n");
                }
                if (files.size() > 15) fsb.append("... 等共 ").append(files.size()).append(" 个文件");
                body3.setText(fsb.toString());
            }

            // Slide 4: 分析列表
            if (!analyses.isEmpty()) {
                XSLFSlide slide4 = ppt.createSlide(contentLayout);
                slide4.getPlaceholder(0).setText("分析任务 (" + analyses.size() + ")");
                XSLFTextShape body4 = slide4.getPlaceholder(1);
                StringBuilder asb = new StringBuilder();
                int limit = Math.min(analyses.size(), 15);
                for (int i = 0; i < limit; i++) {
                    Pipeline p = analyses.get(i);
                    asb.append(p.getName()).append("  [").append(nvl(p.getType())).append("]\n");
                }
                if (analyses.size() > 15) asb.append("... 等共 ").append(analyses.size()).append(" 个任务");
                body4.setText(asb.toString());
            }

            ppt.write(out);
            return out;
        } catch (IOException e) {
            throw new RuntimeException("生成PPT失败", e);
        }
    }

    // ========== 文件树 ==========

    @Override
    public List<FileTreeNode> getFileTree(Long projectId) {
        List<DataFile> files = dataFileMapper.selectByProjectId(projectId, null);

        // 按路径分组，构建树
        Map<String, List<DataFile>> byDir = files.stream()
                .collect(Collectors.groupingBy(f -> {
                    String path = f.getPath() != null ? f.getPath() : "";
                    int lastSlash = path.lastIndexOf('/');
                    return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
                }));

        List<FileTreeNode> result = new ArrayList<>();
        for (Map.Entry<String, List<DataFile>> entry : byDir.entrySet()) {
            String dir = entry.getKey();
            List<FileTreeNode> children = new ArrayList<>();
            for (DataFile f : entry.getValue()) {
                FileTreeNode node = new FileTreeNode(
                        f.getId(), f.getName(), f.getPath(), false,
                        f.getFileSize(), f.getFileType(),
                        f.getCreatedAt() != null ? f.getCreatedAt().format(DATE_FMT) : null
                );
                children.add(node);
            }
            if (dir.isEmpty()) {
                // 根目录文件，直接加到结果
                result.addAll(children);
            } else {
                // 创建目录节点
                FileTreeNode dirNode = new FileTreeNode(null, dir, dir, true, null, null, null);
                dirNode.setChildren(children);
                result.add(dirNode);
            }
        }

        // 按目录优先、然后名称排序
        result.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return result;
    }

    // ========== 批量下载 ==========

    @Override
    public ByteArrayOutputStream batchDownload(Long projectId, List<Long> fileIds, long maxTotalBytes) {
        List<DataFile> files = new ArrayList<>();
        for (Long id : fileIds) {
            DataFile f = dataFileMapper.selectById(id);
            if (f != null && projectId.equals(f.getProjectId())) {
                files.add(f);
            }
        }
        if (files.isEmpty()) throw new RuntimeException("未找到指定文件");

        long totalSize = files.stream().mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0).sum();
        if (totalSize > maxTotalBytes) {
            throw new RuntimeException("文件总大小 " + formatSize(totalSize) + " 超过限制 " + formatSize(maxTotalBytes));
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (DataFile f : files) {
                Path filePath = Paths.get(f.getPath());
                if (!Files.exists(filePath)) {
                    log.warn("文件不存在，跳过: {}", f.getPath());
                    continue;
                }
                zos.putNextEntry(new ZipEntry(f.getName()));
                Files.copy(filePath, zos);
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
}
