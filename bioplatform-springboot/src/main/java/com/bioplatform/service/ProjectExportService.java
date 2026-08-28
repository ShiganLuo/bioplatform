package com.bioplatform.service;

import com.bioplatform.dto.admin.FileTreeNode;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * 项目导出服务：Excel、PPT、文件树、批量下载。
 */
public interface ProjectExportService {

    /**
     * 生成项目 Excel 报表。
     * Sheet1: 项目信息
     * Sheet2: 数据文件列表
     * Sheet3: 分析记录
     */
    ByteArrayOutputStream generateExcel(Long projectId);

    /**
     * 生成项目 PPT 报告。
     */
    ByteArrayOutputStream generatePpt(Long projectId);

    /**
     * 获取项目文件树。
     */
    List<FileTreeNode> getFileTree(Long projectId);

    /**
     * 批量打包下载文件，返回 zip 流。
     * @param fileIds 文件 ID 列表
     * @param maxTotalBytes 最大总字节数，超过抛异常
     */
    ByteArrayOutputStream batchDownload(Long projectId, List<Long> fileIds, long maxTotalBytes);

    /**
     * 按文件路径列表打包下载
     */
    ByteArrayOutputStream batchDownloadByPaths(List<String> filePaths, long maxTotalBytes);
}
