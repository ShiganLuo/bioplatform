package com.bioplatform.enums;

/**
 * Role type enumeration.
 */
public enum RoleTypeEnum {

    USER("ROLE_USER", "普通用户"),
    ADMIN("ROLE_ADMIN", "管理员");

    private final String roleName;
    private final String description;

    RoleTypeEnum(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }
}
