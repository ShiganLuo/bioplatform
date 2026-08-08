package com.bioplatform.dto.admin;

import java.time.LocalDateTime;

/**
 * Admin user operation DTOs.
 */
public final class AdminUserDTO {

    private AdminUserDTO() {
        // utility class
    }

    /**
     * Admin user list item.
     */
    public record AdminUserListDTO(
            Long id,
            String username,
            String email,
            String nickName,
            Integer status,
            LocalDateTime createdAt
    ) {
    }

    /**
     * Admin create user request.
     */
    public record AdminUserCreateRequest(
            String username,
            String email,
            String password,
            String nickName
    ) {
    }

    /**
     * Admin update user request.
     */
    public record AdminUserUpdateRequest(
            Long id,
            String nickName,
            Integer status
    ) {
    }
}
