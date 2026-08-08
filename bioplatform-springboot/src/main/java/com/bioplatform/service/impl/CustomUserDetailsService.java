package com.bioplatform.service.impl;

import com.bioplatform.entity.Role;
import com.bioplatform.entity.User;
import com.bioplatform.mapper.RoleMapper;
import com.bioplatform.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义UserDetailsService实现
 * 从数据库加载用户信息，支持通过用户名或邮箱查找
 *
 * @author luosg
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public CustomUserDetailsService(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 通过用户名或邮箱加载用户
     *
     * @param username 用户名或邮箱
     * @return UserDetails
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 支持通过用户名或邮箱登录
        User user = userMapper.selectByUsernameOrEmail(username);

        if (user == null) {
            log.warn("User not found: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 检查账户是否被禁用
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("User account is disabled: {}", username);
            throw new UsernameNotFoundException("账户已被禁用: " + username);
        }

        // 查询用户角色
        List<Role> roles = roleMapper.selectByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = buildAuthorities(roles);

        // 构建UserDetails
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() != null && user.getStatus() == 1,  // enabled
                true,   // accountNonExpired
                true,   // credentialsNonExpired
                true,   // accountNonLocked
                authorities
        );
    }

    /**
     * 将角色列表转换为GrantedAuthority列表
     * 添加ROLE_前缀
     */
    private List<SimpleGrantedAuthority> buildAuthorities(List<Role> roles) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            for (Role role : roles) {
                // 确保角色名以ROLE_开头
                String roleName = role.getRoleName();
                if (!roleName.startsWith("ROLE_")) {
                    roleName = "ROLE_" + roleName;
                }
                authorities.add(new SimpleGrantedAuthority(roleName));
            }
        }
        return authorities;
    }
}
