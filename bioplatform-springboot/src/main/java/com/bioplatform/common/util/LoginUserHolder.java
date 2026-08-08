package com.bioplatform.common.util;

/**
 * ThreadLocal-based holder for the current logged-in user context.
 */
public final class LoginUserHolder {

    private LoginUserHolder() {
        // utility class
    }

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USERNAME = new ThreadLocal<>();

    /**
     * Get the current user's ID.
     *
     * @return user ID, or null if no user is set
     */
    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * Get the current user's username.
     *
     * @return username, or null if no user is set
     */
    public static String getCurrentUsername() {
        return CURRENT_USERNAME.get();
    }

    /**
     * Set the current user context.
     *
     * @param userId   the user ID
     * @param username the username
     */
    public static void setCurrentUser(Long userId, String username) {
        CURRENT_USER_ID.set(userId);
        CURRENT_USERNAME.set(username);
    }

    /**
     * Clear the current user context. Should be called at the end of each request
     * to prevent memory leaks in thread pools.
     */
    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_USERNAME.remove();
    }
}
