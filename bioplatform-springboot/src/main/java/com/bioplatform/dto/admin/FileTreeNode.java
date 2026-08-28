package com.bioplatform.dto.admin;

import java.util.List;

/**
 * 文件树节点 DTO，用于前端文件浏览器展示。
 */
public class FileTreeNode {
    private Long id;
    private String name;
    private String path;
    private boolean directory;
    private Long size;
    private String fileType;
    private String createdAt;
    private List<FileTreeNode> children;

    public FileTreeNode() {}

    public FileTreeNode(Long id, String name, String path, boolean directory, Long size, String fileType, String createdAt) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.fileType = fileType;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public boolean isDirectory() { return directory; }
    public void setDirectory(boolean directory) { this.directory = directory; }

    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public List<FileTreeNode> getChildren() { return children; }
    public void setChildren(List<FileTreeNode> children) { this.children = children; }
}
