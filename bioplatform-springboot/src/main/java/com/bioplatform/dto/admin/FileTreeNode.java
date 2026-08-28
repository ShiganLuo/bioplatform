package com.bioplatform.dto.admin;

import java.util.List;

/**
 * 文件树节点 DTO，用于前端文件浏览器展示。
 * 基于 PipelineExecution.outputPath 目录扫描生成。
 */
public class FileTreeNode {
    /** 文件/目录名称 */
    private String name;
    /** 相对路径（用于展示） */
    private String path;
    /** 实际磁盘路径（用于下载） */
    private String filePath;
    private boolean directory;
    private Long size;
    private String fileType;
    private String modifiedAt;
    /** 所属执行记录ID */
    private Long executionId;
    /** 所属流程名称 */
    private String pipelineName;
    private List<FileTreeNode> children;

    public FileTreeNode() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }

    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }

    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }

    public List<FileTreeNode> getChildren() { return children; }
    public void setChildren(List<FileTreeNode> children) { this.children = children; }
}
