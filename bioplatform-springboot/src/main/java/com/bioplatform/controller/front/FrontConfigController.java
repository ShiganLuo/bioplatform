package com.bioplatform.controller.front;

import com.bioplatform.dto.common.ApiResponse;
import com.bioplatform.service.SystemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 前台公开站点配置接口（无需登录）
 *
 * @author luosg
 */
@RestController
@RequestMapping("/api/front")
public class FrontConfigController {

    private final SystemService systemService;

    public FrontConfigController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping("/site-config")
    public ApiResponse<Map<String, String>> siteConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("siteName", safeValue("site_name", "BioPlatform"));
        config.put("siteDescription", safeValue("site_description", "一站式生物信息学分析云平台"));
        config.put("contactEmail", safeValue("site_contact_email", "support@bioplatform.com"));
        config.put("githubUrl", safeValue("site_github_url", "https://github.com/bioplatform"));
        return ApiResponse.success(config);
    }

    private String safeValue(String key, String defaultValue) {
        String value = systemService.getConfigValue(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
