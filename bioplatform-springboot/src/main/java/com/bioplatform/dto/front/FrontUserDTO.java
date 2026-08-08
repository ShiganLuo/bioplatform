package com.bioplatform.dto.front;

/**
 * Front-end user operation DTOs.
 */
public final class FrontUserDTO {

    private FrontUserDTO() {
        // utility class
    }

    /**
     * Front-end user info displayed to the user.
     */
    public record FrontUserInfoDTO(
            Long id,
            String username,
            String nickName,
            String avatarUrl
    ) {
    }

    /**
     * Front-end login request.
     */
    public record FrontLoginRequest(
            String username,
            String password
    ) {
    }

    /**
     * Front-end login response.
     */
    public record FrontLoginResponse(
            String accessToken,
            String refreshToken,
            FrontUserInfoDTO userInfo
    ) {
    }

    /**
     * Front-end register request.
     */
    public record FrontRegisterRequest(
            String username,
            String email,
            String password,
            String nickName
    ) {
    }

    /**
     * Refresh token request.
     */
    public record RefreshTokenRequest(
            String refreshToken
    ) {
    }
}
