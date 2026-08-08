package com.bioplatform.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserCreateRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserListDTO;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontRegisterRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;
import com.bioplatform.enums.RoleTypeEnum;
import com.bioplatform.exception.DuplicateUserException;
import com.bioplatform.mapper.UserMapper;
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
import java.util.List;
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
    private final UserRoleMapper userRoleMapper;
    private final JwtTokenProviderUtil jwtTokenProviderUtil;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper,
                           UserRoleMapper userRoleMapper,
                           JwtTokenProviderUtil jwtTokenProviderUtil,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
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

        // 分配默认角色 ROLE_USER
        // 需要查询ROLE_USER的ID，这里假设ID为1，实际应该从数据库查询
        // 简化处理：直接使用角色名称对应的ID
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(1L); // ROLE_USER的ID，默认为1
        userRoleMapper.insert(userRole);

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
    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    @Override
    public PageResult<AdminUserListDTO> listUsers(PageResult<?> query) {
        // 使用PageHelper进行分页查询
        User userParam = new User();
        PageHelper.startPage(1, 10); // 默认分页参数，实际应该从query获取
        List<User> users = userMapper.selectAll(userParam);
        PageInfo<User> pageInfo = new PageInfo<>(users);

        // 转换为DTO
        List<AdminUserListDTO> dtoList = users.stream()
                .map(u -> new AdminUserListDTO(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getNickName(),
                        u.getStatus(),
                        u.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return PageResult.of(pageInfo.getTotal(), (int) pageInfo.getPageNum(), (int) pageInfo.getPageSize(), dtoList);
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
        user.setNickName(request.nickName());
        user.setStatus(1); // 默认启用
        user.setLoginAttempts(0);

        userMapper.insert(user);

        // 分配默认角色 ROLE_USER
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(1L); // ROLE_USER的ID，默认为1
        userRoleMapper.insert(userRole);

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
}
