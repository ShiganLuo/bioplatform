package com.bioplatform.config;

import com.bioplatform.filter.JwtAuthenticationFilter;
import com.bioplatform.service.impl.CustomUserDetailsService;
import com.bioplatform.common.util.JwtTokenProviderUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security配置类
 * 配置JWT无状态认证、白名单路径、CSRF禁用等
 *
 * @author luosg
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProviderUtil jwtTokenProviderUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final SecurityJwtProperties securityJwtProperties;

    public SecurityConfig(JwtTokenProviderUtil jwtTokenProviderUtil,
                          CustomUserDetailsService customUserDetailsService,
                          SecurityJwtProperties securityJwtProperties) {
        this.jwtTokenProviderUtil = jwtTokenProviderUtil;
        this.customUserDetailsService = customUserDetailsService;
        this.securityJwtProperties = securityJwtProperties;
    }

    /**
     * BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * AuthenticationManager配置
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Security过滤器链配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（REST API不需要）
            .csrf(AbstractHttpConfigurer::disable)

            // 启用CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // SecurityContext 存入 request attribute，支持 SSE async dispatch
            .securityContext(sc -> sc
                .securityContextRepository(new RequestAttributeSecurityContextRepository())
            )

            // 禁用表单登录
            .formLogin(AbstractHttpConfigurer::disable)

            // 禁用HTTP Basic认证
            .httpBasic(AbstractHttpConfigurer::disable)

            // 无状态Session（JWT不需要HttpSession）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 响应头配置（禁用FrameOptions以支持Swagger）
            .headers(headers ->
                headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
            )

            // URL授权规则
            .authorizeHttpRequests(auth -> auth
                // 白名单路径：允许匿名访问
                .requestMatchers(securityJwtProperties.getWhitelist().toArray(new String[0])).permitAll()
                // Swagger / API文档路径
                .requestMatchers(
                    "/doc.html",
                    "/webjars/**",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // 前台公开接口
                .requestMatchers("/api/front/**").permitAll()
                // WebSocket
                .requestMatchers("/ws/**").permitAll()
                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )

            // 添加JWT过滤器（在UsernamePasswordAuthenticationFilter之前）
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProviderUtil, customUserDetailsService, securityJwtProperties),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
