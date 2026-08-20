package com.bioplatform.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 *
 * @author luosg
 */
@Data
public class User {
    private Long id;

    private String username;

    private String email;

    private String password;

    private String nickName;

    private String avatarUrl;

    private String phone;

    /** 状态：0=禁用 1=启用 */
    private Integer status;

    /** 上传配额（字节），默认10GB */
    private Long uploadQuota;

    private LocalDateTime lastLoginAt;

    /** 登录尝试次数 */
    private Integer loginAttempts;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
