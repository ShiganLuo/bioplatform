package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI Agent工具实体类
 *
 * @author luosg
 */
@Data
public class AgentTool {
    private Long id;

    private String name;

    private String description;

    private String category;

    private Boolean enabled;

    /** 配置JSON（TEXT类型） */
    private String configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
