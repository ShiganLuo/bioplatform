package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserCreateRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserListDTO;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserUpdateRequest;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.common.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
        // Build a PageResult query object to pass pagination params
        PageResult<AdminUserListDTO> result = userService.listUsers(
                PageResult.of(0, pageNum, pageSize, java.util.List.of()));
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
        // Build a User entity from the request
        com.bioplatform.entity.User user = new com.bioplatform.entity.User();
        user.setId(request.id());
        if (request.nickName() != null) {
            user.setNickName(request.nickName());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userService.updateUser(user);
        return ApiResponse.success();
    }

    /**
     * Enable/disable a user.
     */
    @PutMapping("/status")
    @OperLog(module = "用户管理", operation = "更新用户状态")
    public ApiResponse<Void> updateStatus(@RequestBody Map<String, Object> params) {
        Long id = Long.valueOf(params.get("id").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        userService.updateUserStatus(id, status);
        return ApiResponse.success();
    }
}
