package com.bioplatform.mapper;

import com.bioplatform.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户角色关联Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface UserRoleMapper {

    int insert(UserRole userRole);

    int batchInsert(@Param("list") List<UserRole> list);

    List<UserRole> selectByUserId(@Param("userId") Long userId);

    List<UserRole> selectAll();

    int deleteByUserId(@Param("userId") Long userId);

    int deleteByRoleId(@Param("roleId") Long roleId);

    int deleteById(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
