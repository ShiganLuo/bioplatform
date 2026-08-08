package com.bioplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Security JWT配置属性类
 * 从application.yml的security.jwt前缀读取配置
 *
 * @author luosg
 */
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class SecurityJwtProperties {

    /**
     * JWT白名单URI列表（不需要认证的路径）
     */
    private List<String> whitelist = new ArrayList<>();

    /**
     * JWT Token请求头名称
     */
    private String tokenHeader = "Authorization";

    /**
     * JWT Token前缀
     */
    private String tokenHead = "Bearer ";

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getTokenHead() {
        return tokenHead;
    }

    public void setTokenHead(String tokenHead) {
        this.tokenHead = tokenHead;
    }
}
