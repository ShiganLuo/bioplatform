package com.bioplatform.entity;

import lombok.Data;

/**
 * 角色权限关联表
 *
 * @author luosg
 */
@Data
public class RolePermission {
    private Long id;

    private Long roleId;

    private Long permissionId;
}
