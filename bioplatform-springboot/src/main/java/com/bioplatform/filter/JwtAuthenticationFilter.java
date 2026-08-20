package com.bioplatform.filter;

import com.bioplatform.common.util.JwtTokenProviderUtil;
import com.bioplatform.common.util.LoginUserHolder;
import com.bioplatform.config.SecurityJwtProperties;
import com.bioplatform.service.impl.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 从请求头中提取Bearer Token，验证并设置SecurityContext
 *
 * @author luosg
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProviderUtil jwtTokenProviderUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final SecurityJwtProperties securityJwtProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProviderUtil jwtTokenProviderUtil,
                                   CustomUserDetailsService customUserDetailsService,
                                   SecurityJwtProperties securityJwtProperties) {
        this.jwtTokenProviderUtil = jwtTokenProviderUtil;
        this.customUserDetailsService = customUserDetailsService;
        this.securityJwtProperties = securityJwtProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 白名单URI在无Token时直接放行；若携带了Authorization，则仍尝试解析JWT
            String requestUri = request.getRequestURI();
            String jwt = extractTokenFromRequest(request);
            if (isWhitelisted(requestUri) && !StringUtils.hasText(jwt)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (StringUtils.hasText(jwt) && jwtTokenProviderUtil.validateToken(jwt)) {
                // 从Token中提取用户信息
                Long userId = jwtTokenProviderUtil.getUserIdFromToken(jwt);
                String username = jwtTokenProviderUtil.getUsernameFromToken(jwt);

                // 加载UserDetails
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                if (userDetails != null) {
                    // 创建认证Token
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                        );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 设置SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 设置LoginUserHolder（用于业务层获取当前用户）
                    LoginUserHolder.setCurrentUser(userId, username);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理ThreadLocal，防止内存泄漏
            LoginUserHolder.clear();
        }
    }

    /**
     * 从请求头中提取Bearer Token
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader(securityJwtProperties.getTokenHeader());
        if (StringUtils.hasText(header) && header.startsWith(securityJwtProperties.getTokenHead())) {
            return header.substring(securityJwtProperties.getTokenHead().length());
        }
        return null;
    }

    /**
     * 判断请求URI是否在白名单中
     */
    private boolean isWhitelisted(String requestUri) {
        for (String pattern : securityJwtProperties.getWhitelist()) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }
}
