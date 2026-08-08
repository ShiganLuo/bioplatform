package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色实体类
 *
 * @author luosg
 */
@Data
public class Role {
    private Long id;

    private String roleName;

    private String roleDesc;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
