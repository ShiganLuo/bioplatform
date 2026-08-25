package com.bioplatform.dto.admin;

import java.time.LocalDateTime;

/**
 * Admin pipeline operation DTOs.
 */
public final class AdminPipelineDTO {

    private AdminPipelineDTO() {
        // utility class
    }

    /**
     * Admin pipeline list item.
     */
    public record AdminPipelineListDTO(
            Long id,
            String name,
            String description,
            Long projectId,
            String projectName,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * Admin create pipeline request.
     */
    public record AdminPipelineCreateRequest(
            String name,
            String type,
            Long templateId,
            Long projectId,
            String metaContent,
            String metaType,
            String extraParams,
            String description,
            String category,
            String configJson,
            String dockerImage,
            Integer timeout
    ) {
    }

    /**
     * Admin update pipeline request.
     */
    public record AdminPipelineUpdateRequest(
            Long id,
            String name,
            String type,
            Long templateId,
            Long projectId,
            String metaContent,
            String metaType,
            String extraParams,
            String description,
            String category,
            String configJson,
            String dockerImage,
            Integer timeout
    ) {
    }

    /**
     * Create analysis from project context.
     */
    public record CreateAnalysisRequest(
            String workflowTemplateName,
            String name,
            String metaContent,
            String metaType,
            String extraParams,
            String description
    ) {
    }
}
