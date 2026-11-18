package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.Role;
import com.bioplatform.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin role management controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/roles")
public class AdminRoleController {

    private final RoleService roleService;

    public AdminRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /** 角色列表项（含用户数/权限数） */
    public record RoleListItem(Long id, String roleName, String roleDesc,
                               int userCount, int permCount,
                               java.time.LocalDateTime createdAt) {
    }

    /**
     * List all roles (with user/permission counts).
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<List<RoleListItem>> list() {
        List<RoleListItem> result = new ArrayList<>();
        for (Role role : roleService.listAllRoles()) {
            result.add(new RoleListItem(
                    role.getId(), role.getRoleName(), role.getRoleDesc(),
                    roleService.countRoleUsers(role.getId()),
                    roleService.getRolePermissionIds(role.getId()).size(),
                    role.getCreatedAt()));
        }
        return ApiResponse.success(result);
    }

    /**
     * Create a role.
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('system:role:create')")
    @OperLog(module = "角色管理", operation = "创建角色")
    public ApiResponse<Role> create(@RequestBody Map<String, String> body) {
        try {
            Role role = roleService.createRole(body.get("roleName"), body.get("roleDesc"));
            return ApiResponse.success(role);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * Update role description (role name is immutable after creation).
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", operation = "更新角色")
    public ApiResponse<Void> update(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(String.valueOf(body.get("id")));
        try {
            roleService.updateRoleDesc(id, (String) body.get("roleDesc"));
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * Replace role permissions (full overwrite).
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:edit')")
    @OperLog(module = "角色管理", operation = "分配角色权限")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id,
                                               @RequestBody Map<String, List<Long>> body) {
        try {
            roleService.assignPermissions(id, body.get("permissionIds"));
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * Get role's bound permission ids.
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:list')")
    public ApiResponse<Map<String, Object>> getPermissions(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permissionIds", roleService.getRolePermissionIds(id));
        return ApiResponse.success(result);
    }

    /**
     * Delete a role (built-in / in-use roles rejected).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @OperLog(module = "角色管理", operation = "删除角色")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
