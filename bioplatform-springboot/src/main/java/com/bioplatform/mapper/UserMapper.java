package com.bioplatform.mapper;

import com.bioplatform.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 *
 * @author luosg
 */
@Mapper
public interface UserMapper {

    int insert(User user);

    User selectById(@Param("id") Long id);

    List<User> selectAll(User user);

    int updateById(User user);

    int deleteById(@Param("id") Long id);

    User selectByUsernameOrEmail(@Param("usernameOrEmail") String usernameOrEmail);

    User selectByUsername(@Param("username") String username);

    User selectByEmail(@Param("email") String email);

    int incrementLoginAttempts(@Param("id") Long id);

    int resetLoginAttempts(@Param("id") Long id);

    int countAll(@Param("status") Integer status);
}
