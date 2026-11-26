package com.bioplatform.exception;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.enums.ResultCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
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
     * Handle @PreAuthorize / @PostAuthorize 权限不足
     */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(Exception ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ApiResponse.error(403, "无权访问或操作该资源");
    }

    /**
     * Handle all other uncaught exceptions.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} = {}", ex.getName(), ex.getValue());
        return ApiResponse.error(400,
                "请求参数格式错误: " + ex.getName() + "=" + ex.getValue());
    }

    /**
     * Handle FK / 唯一约束冲突(删被引用的行、重复数据)——给可操作提示而非"服务器内部错误"
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", cause);
        String message = (cause != null && cause.toLowerCase().contains("foreign key"))
                ? "该数据被其他记录引用，无法删除，请先清理关联数据"
                : "数据完整性约束冲突（可能存在重复或非法引用）";
        return ApiResponse.error(ResultCodeEnum.CONFLICT.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return ApiResponse.error(ResultCodeEnum.INTERNAL_ERROR);
    }
}
