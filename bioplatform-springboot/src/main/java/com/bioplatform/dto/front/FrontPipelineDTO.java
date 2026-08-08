package com.bioplatform.dto.front;

import java.time.LocalDateTime;

/**
 * Front-end pipeline DTOs for public viewing.
 */
public final class FrontPipelineDTO {

    private FrontPipelineDTO() {
        // utility class
    }

    /**
     * Public pipeline list item.
     */
    public record FrontPipelineListDTO(
            Long id,
            String name,
            String description,
            Long projectId,
            String projectName,
            Integer status,
            LocalDateTime createdAt
    ) {
    }

    /**
     * Public pipeline detail.
     */
    public record FrontPipelineDetailDTO(
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
}
