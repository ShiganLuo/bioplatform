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
}
