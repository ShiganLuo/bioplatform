package com.bioplatform.dto.common;

import com.bioplatform.enums.ResultCodeEnum;

/**
 * Unified API response wrapper.
 *
 * @param <T> the type of the result data
 */
public record ApiResponse<T>(int code, String message, T result) {

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(
                ResultCodeEnum.SUCCESS.getCode(),
                ResultCodeEnum.SUCCESS.getMessage(),
                null
        );
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ResultCodeEnum.SUCCESS.getCode(),
                ResultCodeEnum.SUCCESS.getMessage(),
                data
        );
    }

    public static <T> ApiResponse<T> error(ResultCodeEnum resultCode) {
        return new ApiResponse<>(
                resultCode.getCode(),
                resultCode.getMessage(),
                null
        );
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
