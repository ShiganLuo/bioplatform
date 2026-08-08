package com.bioplatform.dto.admin;

import java.time.LocalDateTime;

/**
 * Admin project operation DTOs.
 */
public final class AdminProjectDTO {

    private AdminProjectDTO() {
        // utility class
    }

    /**
     * Admin project list item.
     */
    public record AdminProjectListDTO(
            Long id,
            String name,
            String description,
            Long ownerId,
            String ownerUsername,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * Admin create project request.
     */
    public record AdminProjectCreateRequest(
            String name,
            String description,
            Long ownerId
    ) {
    }

    /**
     * Admin update project request.
     */
    public record AdminProjectUpdateRequest(
            Long id,
            String name,
            String description,
            Integer status
    ) {
    }
}
