package com.bioplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;

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
            Long parentId,
            String parentName,
            String name,
            String description,
            String organism,
            String genomeVersion,
            Long ownerId,
            String ownerUsername,
            Integer status,
            Boolean isPrivate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /**
     * Admin create project request.
     */
    public record AdminProjectCreateRequest(
            Long parentId,
            String name,
            String description,
            String organism,
            String genomeVersion,
            Boolean isPrivate,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
    ) {
    }

    /**
     * Admin update project request.
     */
    public record AdminProjectUpdateRequest(
            Long id,
            Long parentId,
            String name,
            String description,
            String organism,
            String genomeVersion,
            Integer status,
            Boolean isPrivate,
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
    ) {
    }
}
