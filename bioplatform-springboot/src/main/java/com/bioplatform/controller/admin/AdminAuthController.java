package com.bioplatform.controller.admin;

import com.bioplatform.common.annotation.OperLog;
import com.bioplatform.common.util.JwtTokenProviderUtil;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.front.FrontUserDTO;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;
import com.bioplatform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

    public AdminAuthController(UserService userService,
                               JwtTokenProviderUtil jwtTokenProviderUtil) {
        this.userService = userService;
        this.jwtTokenProviderUtil = jwtTokenProviderUtil;
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

        FrontUserInfoDTO userInfoDTO = new FrontUserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getNickName(),
                user.getAvatarUrl()
        );
        return ApiResponse.success(userInfoDTO);
    }
}
