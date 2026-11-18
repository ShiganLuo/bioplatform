package com.bioplatform.service.impl;

import com.bioplatform.entity.Permission;
import com.bioplatform.entity.Role;
import com.bioplatform.entity.RolePermission;
import com.bioplatform.mapper.PermissionMapper;
import com.bioplatform.mapper.RoleMapper;
import com.bioplatform.mapper.RolePermissionMapper;
import com.bioplatform.mapper.UserRoleMapper;
import com.bioplatform.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色服务实现类
 *
 * @author luosg
 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    /** 内置角色，不可删除 */
    private static final Set<String> BUILTIN_ROLES = Set.of("ROLE_USER", "ROLE_ADMIN");

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleServiceImpl(RoleMapper roleMapper, PermissionMapper permissionMapper,
                           RolePermissionMapper rolePermissionMapper, UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public List<Role> listAllRoles() {
        Role roleParam = new Role();
        return roleMapper.selectAll(roleParam);
    }

    @Override
    public List<Role> getRolesByUserId(Long userId) {
        return roleMapper.selectByUserId(userId);
    }

    @Override
    public List<Permission> getRolePermissions(Long roleId) {
        return permissionMapper.selectByRoleId(roleId);
    }

    @Override
    public List<String> getPermissionStringsByUserId(Long userId) {
        List<Role> roles = roleMapper.selectByUserId(userId);
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        return getPermissionStringsByRoleIds(roleIds);
    }

    @Override
    public List<String> getPermissionStringsByRoleIds(List<Long> roleIds) {
        Set<String> result = new LinkedHashSet<>();
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                for (Permission p : permissionMapper.selectByRoleId(roleId)) {
                    if (p.getPermission() != null) {
                        result.add(p.getPermission());
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        List<Long> ids = new ArrayList<>();
        for (RolePermission rp : rolePermissionMapper.selectByRoleId(roleId)) {
            ids.add(rp.getPermissionId());
        }
        return ids;
    }

    @Override
    public int countRoleUsers(Long roleId) {
        return userRoleMapper.countByRoleId(roleId);
    }

    @Override
    public Role createRole(String roleName, String roleDesc) {
        String normalized = normalizeRoleName(roleName);
        if (roleMapper.selectByRoleName(normalized) != null) {
            throw new IllegalArgumentException("角色已存在: " + normalized);
        }
        Role role = new Role();
        role.setRoleName(normalized);
        role.setRoleDesc(roleDesc);
        roleMapper.insert(role);
        log.info("创建角色成功: roleId={}, roleName={}", role.getId(), normalized);
        return role;
    }

    @Override
    public void updateRoleDesc(Long id, String roleDesc) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        role.setRoleDesc(roleDesc);
        roleMapper.updateById(role);
        log.info("更新角色描述: roleId={}", id);
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        if (BUILTIN_ROLES.contains(role.getRoleName())) {
            throw new IllegalArgumentException("内置角色不允许删除: " + role.getRoleName());
        }
        int userCount = userRoleMapper.countByRoleId(id);
        if (userCount > 0) {
            throw new IllegalArgumentException("该角色下还有 " + userCount + " 个用户，请先迁移用户再删除");
        }
        rolePermissionMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
        log.info("删除角色成功: roleId={}, roleName={}", id, role.getRoleName());
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        rolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> rows = new ArrayList<>();
            for (Long pid : permissionIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(pid);
                rows.add(rp);
            }
            rolePermissionMapper.batchInsert(rows);
        }
        log.info("角色权限已更新: roleId={}, permCount={}", roleId, permissionIds == null ? 0 : permissionIds.size());
    }

    /** 角色名统一 ROLE_ 前缀（与 CustomUserDetailsService 判定一致） */
    private String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("角色名不能为空");
        }
        String name = roleName.trim().toUpperCase();
        if (!name.startsWith("ROLE_")) {
            name = "ROLE_" + name;
        }
        return name;
    }
}
