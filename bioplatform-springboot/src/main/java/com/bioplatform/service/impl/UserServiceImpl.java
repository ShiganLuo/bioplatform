package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserCreateRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserListDTO;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserResetPasswordRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserUpdateRequest;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontRegisterRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;
import com.bioplatform.enums.RoleTypeEnum;
import com.bioplatform.exception.DuplicateUserException;
import com.bioplatform.entity.Role;
import com.bioplatform.mapper.UserMapper;
import com.bioplatform.mapper.RoleMapper;
import com.bioplatform.mapper.UserRoleMapper;
import com.bioplatform.entity.UserRole;
import com.bioplatform.common.util.JwtTokenProviderUtil;
import com.bioplatform.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 *
 * @author luosg
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final JwtTokenProviderUtil jwtTokenProviderUtil;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper,
                           RoleMapper roleMapper,
                           UserRoleMapper userRoleMapper,
                           JwtTokenProviderUtil jwtTokenProviderUtil,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.jwtTokenProviderUtil = jwtTokenProviderUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public FrontLoginResponse login(String usernameOrEmail, String password) {
        // 根据用户名或邮箱查找用户
        User user = userMapper.selectByUsernameOrEmail(usernameOrEmail);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        // 检查登录尝试次数
        if (user.getLoginAttempts() != null && user.getLoginAttempts() >= 5) {
            throw new IllegalArgumentException("登录尝试次数过多，请稍后再试");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 增加登录尝试次数
            userMapper.incrementLoginAttempts(user.getId());
            throw new IllegalArgumentException("密码错误");
        }

        // 重置登录尝试次数并更新最后登录时间
        userMapper.resetLoginAttempts(user.getId());

        // 生成token
        String accessToken = jwtTokenProviderUtil.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = jwtTokenProviderUtil.generateRefreshToken(user.getId(), user.getUsername());

        // 构建用户信息DTO
        FrontUserInfoDTO userInfoDTO = new FrontUserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getNickName(),
                user.getAvatarUrl()
        );

        log.info("用户登录成功: {}", user.getUsername());
        return new FrontLoginResponse(accessToken, refreshToken, userInfoDTO);
    }

    @Override
    @Transactional
    public FrontUserInfoDTO register(FrontRegisterRequest request) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(request.username());
        if (existingUser != null) {
            throw new DuplicateUserException("用户名已存在");
        }

        // 检查邮箱是否已存在
        existingUser = userMapper.selectByEmail(request.email());
        if (existingUser != null) {
            throw new DuplicateUserException("邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickName(request.nickName());
        user.setStatus(1); // 默认启用
        user.setLoginAttempts(0);

        userMapper.insert(user);

        assignRoles(user.getId(), null, true);

        log.info("用户注册成功: {}", user.getUsername());

        return new FrontUserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getNickName(),
                user.getAvatarUrl()
        );
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    @Transactional
    public void updateUser(AdminUserUpdateRequest request) {
        User user = userMapper.selectById(request.id());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (request.email() != null && !request.email().isBlank()) {
            User existingUser = userMapper.selectByEmail(request.email());
            if (existingUser != null && !Objects.equals(existingUser.getId(), request.id())) {
                throw new DuplicateUserException("邮箱已被注册");
            }
            user.setEmail(request.email());
        }

        user.setNickName(request.nickname());
        user.setPhone(request.phone());
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userMapper.updateById(user);

        if (request.roles() != null) {
            assignRoles(user.getId(), request.roles(), false);
        }
    }

    @Override
    public PageResult<AdminUserListDTO> listUsers(int pageNum, int pageSize, String keyword, Integer status) {
        User userParam = new User();
        userParam.setUsername(keyword);
        userParam.setEmail(keyword);
        userParam.setStatus(status);

        PageHelper.startPage(pageNum, pageSize);
        List<User> users = userMapper.selectAll(userParam);
        PageInfo<User> pageInfo = new PageInfo<>(users);

        Map<Long, List<String>> roleNameMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> roleMapper.selectByUserId(user.getId()).stream()
                                .map(Role::getRoleName)
                                .collect(Collectors.toList())
                ));

        List<AdminUserListDTO> dtoList = users.stream()
                .map(u -> new AdminUserListDTO(
                        u.getId(),
                        u.getUsername(),
                        u.getNickName(),
                        u.getEmail(),
                        u.getPhone(),
                        roleNameMap.getOrDefault(u.getId(), List.of()),
                        u.getStatus(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return PageResult.of(pageInfo.getTotal(), pageNum, pageSize, dtoList);
    }

    @Override
    @Transactional
    public void createUser(AdminUserCreateRequest request) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(request.username());
        if (existingUser != null) {
            throw new DuplicateUserException("用户名已存在");
        }

        // 检查邮箱是否已存在
        existingUser = userMapper.selectByEmail(request.email());
        if (existingUser != null) {
            throw new DuplicateUserException("邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickName(request.nickname());
        user.setPhone(request.phone());
        user.setStatus(1);
        user.setLoginAttempts(0);

        userMapper.insert(user);

        assignRoles(user.getId(), request.roles(), true);

        log.info("管理员创建用户成功: {}", user.getUsername());
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        user.setStatus(status);
        userMapper.updateById(user);

        log.info("更新用户状态: userId={}, status={}", id, status);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        log.info("删除用户: userId={}", id);
    }

    @Override
    public void resetUserPassword(AdminUserResetPasswordRequest request) {
        User user = userMapper.selectById(request.id());
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        log.info("重置用户密码: userId={}", request.id());

    }

    private void assignRoles(Long userId, List<String> requestedRoles, boolean fallbackToDefaultUserRole) {
        List<String> normalizedRoles = normalizeRoles(requestedRoles);
        if (normalizedRoles.isEmpty() && fallbackToDefaultUserRole) {
            normalizedRoles = List.of(RoleTypeEnum.USER.getRoleName());
        }

        userRoleMapper.deleteByUserId(userId);
        if (normalizedRoles.isEmpty()) {
            return;
        }

        List<UserRole> userRoles = new ArrayList<>();
        for (String roleName : normalizedRoles) {
            Role role = roleMapper.selectByRoleName(roleName);
            if (role == null) {
                throw new IllegalArgumentException("角色不存在: " + roleName);
            }
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoles.add(userRole);
        }

        userRoleMapper.batchInsert(userRoles);
    }

    private List<String> normalizeRoles(List<String> requestedRoles) {
        if (requestedRoles == null) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String role : requestedRoles) {
            if (role == null || role.isBlank()) {
                continue;
            }

            String upper = role.trim().toUpperCase(Locale.ROOT);
            if (!upper.startsWith("ROLE_")) {
                upper = "ROLE_" + upper;
            }
            normalized.add(upper);
        }

        return List.copyOf(normalized);
    }

}
