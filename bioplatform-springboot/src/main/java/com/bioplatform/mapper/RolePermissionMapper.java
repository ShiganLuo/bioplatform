package com.bioplatform.mapper;

import com.bioplatform.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色权限关联Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface RolePermissionMapper {

    int insert(RolePermission rolePermission);

    int batchInsert(@Param("list") List<RolePermission> list);

    List<RolePermission> selectByRoleId(@Param("roleId") Long roleId);

    List<RolePermission> selectByPermissionId(@Param("permissionId") Long permissionId);

    List<RolePermission> selectAll();

    int deleteByRoleId(@Param("roleId") Long roleId);

    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    int deleteById(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
