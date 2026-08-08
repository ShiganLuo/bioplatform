package com.bioplatform.dto.front;

import java.time.LocalDateTime;

/**
 * Front-end project DTOs for public viewing.
 */
public final class FrontProjectDTO {

    private FrontProjectDTO() {
        // utility class
    }

    /**
     * Public project list item.
     */
    public record FrontProjectListDTO(
            Long id,
            String name,
            String description,
            String ownerNickName,
            LocalDateTime createdAt
    ) {
    }

    /**
     * Public project detail.
     */
    public record FrontProjectDetailDTO(
            Long id,
            String name,
            String description,
            Long ownerId,
            String ownerNickName,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
