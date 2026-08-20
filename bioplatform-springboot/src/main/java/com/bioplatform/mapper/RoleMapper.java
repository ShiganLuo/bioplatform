package com.bioplatform.mapper;

import com.bioplatform.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface RoleMapper {

    int insert(Role role);

    Role selectById(@Param("id") Long id);

    List<Role> selectAll(Role role);

    int updateById(Role role);

    int deleteById(@Param("id") Long id);

    Role selectByRoleName(@Param("roleName") String roleName);

    List<Role> selectByUserId(@Param("userId") Long userId);

    List<Role> selectRolesWithPermissions();
}
