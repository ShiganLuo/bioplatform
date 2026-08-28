package com.bioplatform.controller.front;

import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontRegisterRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;
import com.bioplatform.service.EmailCodeService;
import com.bioplatform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Front-end authentication controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front/auth")
public class FrontAuthController {

    private final UserService userService;
    private final EmailCodeService emailCodeService;

    public FrontAuthController(UserService userService, EmailCodeService emailCodeService) {
        this.userService = userService;
        this.emailCodeService = emailCodeService;
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/sendEmailCode")
    public ApiResponse<Void> sendEmailCode(@RequestBody Map<String, String> params) {
        String email = params.get("email");
        if (email == null || email.isBlank()) {
            return ApiResponse.error(400, "邮箱不能为空");
        }
        try {
            emailCodeService.sendCode(email.trim());
            return ApiResponse.success();
        } catch (Exception e) {
            return ApiResponse.error(500, "验证码发送失败: " + e.getMessage());
        }
    }

    /**
     * User registration (需要邮箱验证码).
     */
    @PostMapping("/register")
    public ApiResponse<FrontUserInfoDTO> register(@RequestBody @Valid FrontRegisterRequest request) {
        if (request.verifyCode() == null || request.verifyCode().isBlank()) {
            return ApiResponse.error(400, "请输入邮箱验证码");
        }
        if (!emailCodeService.verifyCode(request.email(), request.verifyCode())) {
            return ApiResponse.error(400, "验证码错误或已过期");
        }
        FrontUserInfoDTO userInfo = userService.register(request);
        return ApiResponse.success(userInfo);
    }

    /**
     * User login.
     */
    @PostMapping("/login")
    public ApiResponse<FrontLoginResponse> login(@RequestBody @Valid FrontLoginRequest request) {
        FrontLoginResponse response = userService.login(request.username(), request.password());
        return ApiResponse.success(response);
    }

    /**
     * User logout.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success();
    }

    /**
     * Get current user info.
     */
    @GetMapping("/userInfo")
    public ApiResponse<FrontUserInfoDTO> getUserInfo() {
        Long userId = LoginUserHolder.getCurrentUserId();
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
