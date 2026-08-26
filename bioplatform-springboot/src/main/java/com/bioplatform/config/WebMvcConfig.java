package com.bioplatform.config;

import com.bioplatform.websocket.FeedbackWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Web MVC配置类
 * 配置CORS跨域、静态资源映射、WebSocket
 * 
 * @author luosg
 */
@Configuration
@EnableWebSocket
public class WebMvcConfig implements WebMvcConfigurer, WebSocketConfigurer {

    private final String uploadDir;
    private final String ipPrefix;
    private final FeedbackWebSocketHandler feedbackWebSocketHandler;

    /**
     * 构造器注入配置属性
     *
     * @param uploadDir 文件上传目录
     * @param ipPrefix 文件访问IP前缀
     */
    public WebMvcConfig(
            @Value("${app.upload.dir:/home/luosg/uploads/bioplatform}") String uploadDir,
            @Value("${app.upload.ipPrefix:http://localhost:8080}") String ipPrefix,
            FeedbackWebSocketHandler feedbackWebSocketHandler) {
        this.uploadDir = uploadDir;
        this.ipPrefix = ipPrefix;
        this.feedbackWebSocketHandler = feedbackWebSocketHandler;
    }

    /**
     * 配置CORS跨域访问
     * 开发环境允许所有来源，生产环境应限制具体域名
     * 
     * @param registry CORS注册表
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // 所有路径
                .allowedOriginPatterns("*")  // 允许所有来源（生产环境应限制）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")  // 允许的HTTP方法
                .allowedHeaders("*")  // 允许所有请求头
                .exposedHeaders("Authorization", "X-Token")  // 暴露的响应头
                .allowCredentials(true)  // 允许携带凭证（Cookie等）
                .maxAge(3600);  // 预检请求缓存时间（秒）
    }

    /**
     * 配置静态资源映射
     * 将上传文件目录映射为可访问的URL路径
     * 
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射上传文件目录为/static/upload/**
        registry.addResourceHandler("/static/upload/**")
                .addResourceLocations("file:" + uploadDir + "/");

        // 映射静态资源
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * 注册WebSocket处理器
     * 用于实时通信（如任务进度推送、日志流等）
     * 
     * @param registry WebSocket处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(feedbackWebSocketHandler, "/ws/feedback")
                .setAllowedOrigins("*");
    }
}
