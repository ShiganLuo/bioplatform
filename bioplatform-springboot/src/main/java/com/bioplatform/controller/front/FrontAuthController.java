package com.bioplatform.controller.front;

import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontLoginResponse;
import com.bioplatform.dto.front.FrontUserDTO.FrontRegisterRequest;
import com.bioplatform.dto.front.FrontUserDTO.FrontUserInfoDTO;
import com.bioplatform.entity.User;
import com.bioplatform.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Front-end authentication controller.
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front/auth")
public class FrontAuthController {

    private final UserService userService;

    public FrontAuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * User registration.
     */
    @PostMapping("/register")
    public ApiResponse<FrontUserInfoDTO> register(@RequestBody @Valid FrontRegisterRequest request) {
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
