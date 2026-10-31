package com.bioplatform.worker;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Worker 端点鉴权过滤器
 * 配置了 WORKER_TOKEN 环境变量后,所有请求必须携带匹配的 X-Worker-Token 头,
 * 否则 401 —— 堵住 /worker/shell/execute 等任意命令执行面。
 * 未配置 token(开发环境)则放行,保持兼容。
 *
 * @author luosg
 */
@Component
public class WorkerTokenFilter extends OncePerRequestFilter {

    @Value("${worker.token:}")
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return expectedToken == null || expectedToken.isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader("X-Worker-Token");
        if (expectedToken.equals(provided)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"missing or invalid X-Worker-Token\"}");
    }
}
