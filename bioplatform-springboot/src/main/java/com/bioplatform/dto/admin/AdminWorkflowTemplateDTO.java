package com.bioplatform.dto.admin;

import java.util.List;

/**
 * WorkflowTemplate 相关 DTO
 */
public final class AdminWorkflowTemplateDTO {

    private AdminWorkflowTemplateDTO() {}

    /**
     * 创建/更新模板请求
     */
    public record CreateRequest(
            String name,
            String description,
            String type,
            String category,
            String configTemplate,
            String schemaJson,
            String snakemakePath,
            String icon,
            Integer sortOrder
    ) {}

    /**
     * 更新模板请求（含 id）
     */
    public record UpdateRequest(
            Long id,
            String name,
            String description,
            String type,
            String category,
            String configTemplate,
            String schemaJson,
            String snakemakePath,
            String icon,
            Integer sortOrder,
            Boolean enabled
    ) {}

    /**
     * 批量导入请求
     */
    public record ImportRequest(
            String omicsDir
    ) {}
}
