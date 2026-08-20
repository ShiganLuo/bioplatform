package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserCreateRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserResetPasswordRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserListDTO;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserStatusUpdateRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserUpdateRequest;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Admin user management controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final com.bioplatform.service.UserService userService;

    public AdminUserController(com.bioplatform.service.UserService userService) {
        this.userService = userService;
    }

    /**
     * Paginated user list with optional keyword/status filter.
     */
    @GetMapping("/list")
    public ApiResponse<PageResult<AdminUserListDTO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        PageResult<AdminUserListDTO> result = userService.listUsers(pageNum, pageSize, keyword, status);
        return ApiResponse.success(result);
    }

    /**
     * Create a new user.
     */
    @PostMapping("/create")
    @OperLog(module = "用户管理", operation = "创建用户")
    public ApiResponse<Void> create(@RequestBody @Valid AdminUserCreateRequest request) {
        userService.createUser(request);
        return ApiResponse.success();
    }

    /**
     * Update user info.
     */
    @PutMapping("/update")
    @OperLog(module = "用户管理", operation = "更新用户")
    public ApiResponse<Void> update(@RequestBody @Valid AdminUserUpdateRequest request) {
        userService.updateUser(request);
        return ApiResponse.success();
    }

    /**
     * Enable/disable a user.
     */
    @PutMapping("/status")
    @OperLog(module = "用户管理", operation = "更新用户状态")
    public ApiResponse<Void> updateStatus(@RequestBody @Valid AdminUserStatusUpdateRequest request) {
        userService.updateUserStatus(request.id(), request.status());
        return ApiResponse.success();
    }

    /**
     * Delete a user.
     */
    @DeleteMapping("/{id}")
    @OperLog(module = "用户管理", operation = "删除用户")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success();
    }

    /**
     * Reset user password.
     */
    @PutMapping("/reset-password")
    @OperLog(module = "用户管理", operation = "重置用户密码")
    public ApiResponse<Void> resetPassword(@RequestBody @Valid AdminUserResetPasswordRequest request) {
        userService.resetUserPassword(request);
        return ApiResponse.success();
    }
}
