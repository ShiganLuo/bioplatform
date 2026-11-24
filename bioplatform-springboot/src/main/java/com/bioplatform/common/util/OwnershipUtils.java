package com.bioplatform.common.util;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 数据归属校验工具
 * 用于行级权限控制：普通用户只能操作自己的数据，管理员可操作所有数据
 */
public final class OwnershipUtils {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private OwnershipUtils() {
        // utility class
    }

    /**
     * 判断当前用户是否为管理员
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    /**
     * 判断当前用户是否持有指定权限标识（管理员视为持有全部权限）。
     * 权限标识来自 authorities（ROLE_角色名 + 角色全部 permission 标识，每请求 DB 现查）。
     */
    public static boolean hasAuthority(String authority) {
        if (authority == null || authority.isBlank()) return true;
        if (isAdmin()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    /**
     * 获取当前用户ID
     */
    public static Long getCurrentUserId() {
        return LoginUserHolder.getCurrentUserId();
    }

    /**
     * 校验数据归属：非管理员且非本人 → 抛出 AccessDeniedException
     *
     * @param ownerId      数据所有者ID
     * @param resourceName 资源名称（用于错误信息）
     * @throws AccessDeniedException 无权访问时抛出
     */
    public static void checkOwnership(Long ownerId, String resourceName) {
        if (ownerId == null) return;
        if (isAdmin()) return;
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(ownerId)) {
            throw new AccessDeniedException("无权访问该" + resourceName);
        }
    }

    /**
     * 校验项目归属（通过项目的 ownerId）
     *
     * @param projectOwnerId 项目所有者ID
     * @throws AccessDeniedException 无权访问时抛出
     */
    public static void checkProjectOwnership(Long projectOwnerId) {
        checkOwnership(projectOwnerId, "项目");
    }

    /**
     * 校验项目读取可见性：管理员、项目所有者、或公开项目 → 允许读取。
     * 写操作仍走 {@link #checkProjectOwnership}。
     *
     * @param ownerId   项目所有者ID
     * @param isPrivate 是否私有（true=私有 false/null=公开）
     * @throws AccessDeniedException 无权访问时抛出
     */
    public static void checkProjectReadAccess(Long ownerId, Boolean isPrivate) {
        if (isAdmin()) return;
        if (!Boolean.TRUE.equals(isPrivate)) return; // 公开项目所有人可见
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null || !currentUserId.equals(ownerId)) {
            throw new AccessDeniedException("无权访问该项目");
        }
    }
}
