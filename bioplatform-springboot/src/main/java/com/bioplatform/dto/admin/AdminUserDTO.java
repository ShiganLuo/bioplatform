package com.bioplatform.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

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
            String nickname,
            String email,
            String phone,
            List<String> roles,
            Integer status,
            LocalDateTime createTime
    ) {
    }

    /**
     * Admin create user request.
     */
    public record AdminUserCreateRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 20, message = "用户名长度需在 3-20 个字符之间")
            String username,

            @Email(message = "邮箱格式不正确")
            String email,

            @NotBlank(message = "密码不能为空")
            @Size(min = 6, message = "密码长度不能少于 6 位")
            String password,

            String nickname,

            String phone,

            List<String> roles
    ) {
    }

    /**
     * Admin update user request.
     */
    public record AdminUserUpdateRequest(
            @NotNull(message = "用户ID不能为空")
            Long id,

            String nickname,

            @Email(message = "邮箱格式不正确")
            String email,

            String phone,

            List<String> roles,

            Integer status
    ) {
    }

    public record AdminUserStatusUpdateRequest(
            @NotNull(message = "用户ID不能为空")
            Long id,

            @NotNull(message = "状态不能为空")
            Integer status
    ) {
    }

    /**
     * Admin reset password request.
     */
    public record AdminUserResetPasswordRequest(
            @NotNull(message = "用户ID不能为空")
            Long id,

            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, message = "密码长度不能少于 6 位")
            String newPassword
    ) {

    }
}
