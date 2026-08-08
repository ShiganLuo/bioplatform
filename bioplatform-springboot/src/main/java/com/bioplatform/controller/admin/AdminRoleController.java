package com.bioplatform.controller.admin;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.entity.Role;
import com.bioplatform.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * List all roles.
     */
    @GetMapping("/list")
    public ApiResponse<List<Role>> list() {
        List<Role> roles = roleService.listAllRoles();
        return ApiResponse.success(roles);
    }
}
