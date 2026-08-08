package com.bioplatform.mapper;

import com.bioplatform.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface PermissionMapper {

    int insert(Permission permission);

    Permission selectById(@Param("id") Long id);

    List<Permission> selectAll(Permission permission);

    int updateById(Permission permission);

    int deleteById(@Param("id") Long id);

    List<Permission> selectByRoleId(@Param("roleId") Long roleId);

    List<Permission> selectTree();

    List<Permission> selectChildrenById(@Param("id") Long id);

    List<Permission> selectByType(@Param("type") String type);
}
