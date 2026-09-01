package com.bioplatform.exception;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.enums.ResultCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle bean validation failures (@Valid).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ApiResponse.error(ResultCodeEnum.VALIDATION_ERROR.getCode(), message);
    }

    /**
     * Handle illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ApiResponse.error(ResultCodeEnum.BAD_REQUEST.getCode(), ex.getMessage());
    }

    /**
     * Handle duplicate user registration.
     */
    @ExceptionHandler(DuplicateUserException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDuplicateUserException(DuplicateUserException ex) {
        log.warn("Duplicate user: {}", ex.getMessage());
        return ApiResponse.error(ResultCodeEnum.CONFLICT.getCode(), ex.getMessage());
    }

    /**
     * Handle SSE/async IOException (Broken pipe) — 客户端断开连接，非服务端错误
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex) {
        log.debug("SSE连接断开（客户端断开）: {}", ex.getMessage());
        // SSE 连接断开不需要返回响应，直接忽略
    }

    /**
     * Handle all other uncaught exceptions.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiResponse.error(ResultCodeEnum.INTERNAL_ERROR);
    }
}
