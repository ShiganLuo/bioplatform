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
            String description,
            Long projectId
    ) {
    }

    /**
     * Admin update pipeline request.
     */
    public record AdminPipelineUpdateRequest(
            Long id,
            String name,
            String description,
            Integer status
    ) {
    }
}
