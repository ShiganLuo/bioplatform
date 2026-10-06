package com.bioplatform.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具：在任意 Spring 线程（controller/service/切面）中获取
 * 客户端真实 IP 与接口标识，供操作审计使用。
 *
 * @author luosg
 */
public final class RequestContextUtil {

    private RequestContextUtil() {
    }

    /**
     * 当前请求的接口标识，如 "POST /api/admin/auth/login"；无请求上下文时返回 null
     */
    public static String getEndpoint() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getMethod() + " " + request.getRequestURI();
    }

    /**
     * 客户端真实 IP（优先 X-Forwarded-For → X-Real-IP → remoteAddr）；无请求上下文时返回 null
     */
    public static String getClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes == null ? null : attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}
