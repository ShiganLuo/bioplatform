package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.JwtTokenProviderUtil;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.front.FrontUserDTO;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;
import com.bioplatform.service.RoleService;
import com.bioplatform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin authentication controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final UserService userService;
    private final JwtTokenProviderUtil jwtTokenProviderUtil;
    private final RoleService roleService;
    private final com.bioplatform.service.EmailCodeService emailCodeService;

    public AdminAuthController(UserService userService,
                               JwtTokenProviderUtil jwtTokenProviderUtil,
                               RoleService roleService,
                               com.bioplatform.service.EmailCodeService emailCodeService) {
        this.userService = userService;
        this.jwtTokenProviderUtil = jwtTokenProviderUtil;
        this.roleService = roleService;
        this.emailCodeService = emailCodeService;
    }

    /**
     * Admin login.
     */
    @PostMapping("/login")
    public ApiResponse<FrontLoginResponse> login(@RequestBody @Valid FrontLoginRequest request) {
        FrontLoginResponse response = userService.login(request.username(), request.password());
        return ApiResponse.success(response);
    }

    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refreshToken")
    public ApiResponse<Map<String, String>> refreshToken(@RequestBody FrontUserDTO.RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.error(400, "refreshToken不能为空");
        }

        if (!jwtTokenProviderUtil.validateToken(refreshToken)) {
            return ApiResponse.error(401, "refreshToken无效或已过期");
        }

        Long userId = jwtTokenProviderUtil.getUserIdFromToken(refreshToken);
        String username = jwtTokenProviderUtil.getUsernameFromToken(refreshToken);

        String newAccessToken = jwtTokenProviderUtil.generateAccessToken(userId, username);

        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", newAccessToken);
        tokens.put("refreshToken", refreshToken);
        return ApiResponse.success(tokens);
    }

    /**
     * Get current user info with roles and permissions.
     */
    @GetMapping("/userInfo")
    public ApiResponse<FrontUserInfoDTO> getUserInfo() {
        Long userId = com.bioplatform.common.util.LoginUserHolder.getCurrentUserId();
        User user = userService.getUserById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        List<com.bioplatform.entity.Role> roles = roleService.getRolesByUserId(userId);
        List<String> roleNames = roles.stream()
                .map(com.bioplatform.entity.Role::getRoleName)
                .collect(java.util.stream.Collectors.toList());

        FrontUserInfoDTO userInfoDTO = new FrontUserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getNickName(),
                user.getAvatarUrl(),
                roleNames
        );
        return ApiResponse.success(userInfoDTO);
    }

    /**
     * Admin logout.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }

    /**
     * 发送重置密码验证码（无需登录）
     */
    @PostMapping("/sendResetCode")
    public ApiResponse<Void> sendResetCode(@RequestBody Map<String, String> params) {
        String email = params.get("email");
        if (email == null || email.isBlank()) {
            return ApiResponse.error(400, "邮箱不能为空");
        }
        // 检查邮箱是否已注册
        User user = userService.getUserByEmail(email.trim());
        if (user == null) {
            return ApiResponse.error(404, "该邮箱未注册");
        }
        try {
            emailCodeService.sendCodeForReset(email.trim());
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "验证码发送失败: " + e.getMessage());
        }
    }

    /**
     * 通过邮箱验证码重置密码（无需登录）
     */
    @PostMapping("/resetPassword")
    public ApiResponse<Void> resetPassword(@RequestBody Map<String, String> params) {
        String email = params.get("email");
        String code = params.get("code");
        String newPassword = params.get("newPassword");

        if (email == null || email.isBlank()) {
            return ApiResponse.error(400, "邮箱不能为空");
        }
        if (code == null || code.isBlank()) {
            return ApiResponse.error(400, "验证码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ApiResponse.error(400, "密码长度不能少于6位");
        }

        if (!emailCodeService.verifyCode(email.trim(), code.trim())) {
            return ApiResponse.error(400, "验证码错误或已过期");
        }

        try {
            userService.resetPasswordByEmail(email.trim(), newPassword);
            return ApiResponse.success();
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
