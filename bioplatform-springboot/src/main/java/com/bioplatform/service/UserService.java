package com.bioplatform.service;

import com.bioplatform.dto.admin.AdminUserDTO.AdminUserCreateRequest;
import com.bioplatform.dto.admin.AdminUserDTO.AdminUserListDTO;
import com.bioplatform.dto.common.PageResult;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontRegisterRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;

/**
 * 用户服务接口
 *
 * @author luosg
 */
public interface UserService {

    /**
     * 用户登录
     *
     * @param usernameOrEmail 用户名或邮箱
     * @param password        密码
     * @return 登录响应（包含token和用户信息）
     */
    FrontLoginResponse login(String usernameOrEmail, String password);

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户信息
     */
    FrontUserInfoDTO register(FrontRegisterRequest request);

    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 用户信息
     */
    User getUserById(Long id);

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户信息
     */
    User getUserByUsername(String username);

    /**
     * 更新用户信息
     *
     * @param user 用户信息
     */
    void updateUser(User user);

    /**
     * 分页查询用户列表
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResult<AdminUserListDTO> listUsers(PageResult<?> query);

    /**
     * 管理员创建用户
     *
     * @param request 创建用户请求
     */
    void createUser(AdminUserCreateRequest request);

    /**
     * 更新用户状态
     *
     * @param id     用户ID
     * @param status 状态（0=禁用 1=启用）
     */
    void updateUserStatus(Long id, Integer status);
}
