package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 权限实体类
 *
 * @author luosg
 */
@Data
public class Permission {
    private Long id;

    private String name;

    private String permission;

    /** 类型：menu=菜单, button=按钮, api=接口 */
    private String type;

    private Long parentId;

    private String path;

    private Integer orderNum;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
