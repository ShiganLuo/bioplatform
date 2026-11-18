package com.bioplatform.service;

import com.bioplatform.entity.Permission;
import com.bioplatform.entity.Role;

import java.util.List;

/**
 * 角色服务接口
 *
 * @author luosg
 */
public interface RoleService {

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    List<Role> listAllRoles();

    /**
     * 根据用户ID查询角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> getRolesByUserId(Long userId);

    /**
     * 根据角色ID查询权限列表
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    List<Permission> getRolePermissions(Long roleId);

    /**
     * 用户的权限标识列表（多角色权限并集，如 system:user:list）
     */
    List<String> getPermissionStringsByUserId(Long userId);

    /**
     * 角色ID列表对应的权限标识列表（并集去重）
     */
    List<String> getPermissionStringsByRoleIds(List<Long> roleIds);

    /**
     * 角色当前绑定的权限ID列表
     */
    List<Long> getRolePermissionIds(Long roleId);

    /**
     * 角色下的用户数（删除保护用）
     */
    int countRoleUsers(Long roleId);

    /**
     * 新建角色（roleName 自动补 ROLE_ 前缀，查重）
     */
    Role createRole(String roleName, String roleDesc);

    /**
     * 更新角色描述（角色名建后不可改）
     */
    void updateRoleDesc(Long id, String roleDesc);

    /**
     * 删除角色。内置角色（ROLE_USER/ROLE_ADMIN）与有用户引用的角色拒绝；
     * 级联清理 role_permissions。
     */
    void deleteRole(Long id);

    /**
     * 全量替换角色的权限绑定
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);
}
