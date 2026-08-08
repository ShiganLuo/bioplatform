package com.bioplatform.agent.tools.impl;

import com.bioplatform.agent.tools.Tool;
import com.bioplatform.entity.DataFile;
import com.bioplatform.mapper.DataFileMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件信息工具 - 读取数据文件的元数据信息
 *
 * @author luosg
 */
@Component
public class FileInfoTool implements Tool {

    private final DataFileMapper dataFileMapper;
    private final ObjectMapper objectMapper;

    public FileInfoTool(DataFileMapper dataFileMapper, ObjectMapper objectMapper) {
        this.dataFileMapper = dataFileMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "file_info";
    }

    @Override
    public String getDescription() {
        return "读取生物信息学数据文件的元数据信息，包括文件名、类型、大小、物种、基因组版本等。" +
                "通过文件ID查询文件详情。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> fileIdProp = new HashMap<>();
        fileIdProp.put("type", "integer");
        fileIdProp.put("description", "文件ID");
        properties.put("file_id", fileIdProp);

        schema.put("properties", properties);

        java.util.List<String> required = new java.util.ArrayList<>();
        required.add("file_id");
        schema.put("required", required);

        return schema;
    }

    @Override
    public String execute(Map<String, String> args) {
        try {
            String fileIdStr = args.get("file_id");
            if (fileIdStr == null || fileIdStr.isEmpty()) {
                return "{\"error\": \"缺少必需参数 file_id\"}";
            }

            Long fileId = Long.parseLong(fileIdStr);
            DataFile dataFile = dataFileMapper.selectById(fileId);

            if (dataFile == null) {
                return "{\"error\": \"文件不存在: ID=" + fileId + "\"}";
            }

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("id", dataFile.getId());
            fileInfo.put("name", dataFile.getName());
            fileInfo.put("path", dataFile.getPath());
            fileInfo.put("file_type", dataFile.getFileType());
            fileInfo.put("file_size", dataFile.getFileSize());
            fileInfo.put("file_size_human", formatFileSize(dataFile.getFileSize()));
            fileInfo.put("organism", dataFile.getOrganism());
            fileInfo.put("genome_version", dataFile.getGenomeVersion());
            fileInfo.put("project_id", dataFile.getProjectId());
            fileInfo.put("created_at", dataFile.getCreatedAt() != null ? dataFile.getCreatedAt().toString() : null);

            // 添加文件类型说明
            fileInfo.put("file_type_description", getFileTypeDescription(dataFile.getFileType()));

            return objectMapper.writeValueAsString(fileInfo);
        } catch (NumberFormatException e) {
            return "{\"error\": \"文件ID格式无效\"}";
        } catch (Exception e) {
            return "{\"error\": \"查询文件信息失败: " + e.getMessage() + "\"}";
        }
    }

    private String formatFileSize(Long size) {
        if (size == null) return "未知";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    private String getFileTypeDescription(String fileType) {
        if (fileType == null) return "未知文件类型";
        return switch (fileType.toUpperCase()) {
            case "VCF" -> "变异调用格式文件，包含SNP和Indel变异信息";
            case "BAM", "SAM" -> "序列比对文件，存储reads比对到参考基因组的信息";
            case "FASTA", "FA", "FAA", "FNA" -> "序列文件，存储DNA或蛋白质序列";
            case "FASTQ", "FQ" -> "测序原始数据文件，包含序列和质量值";
            case "BED" -> "基因组区间文件，定义基因组上的区域";
            case "GFF", "GTF" -> "基因注释文件，描述基因结构信息";
            case "BIGWIG", "BW" -> "基因组信号数据文件";
            case "CRAM" -> "压缩比对文件，BAM的高效压缩格式";
            default -> fileType + "格式文件";
        };
    }
}
