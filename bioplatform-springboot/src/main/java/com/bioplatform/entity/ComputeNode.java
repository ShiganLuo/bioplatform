package com.bioplatform.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 计算节点实体
 */
@Data
public class ComputeNode {
    private Long id;
    private String nodeId;
    private String hostname;
    private String url;
    private Integer cpuCores;
    private Long memoryMb;
    /** 0=禁用 1=启用 */
    private Integer status;
    /** 0=离线 1=在线 */
    private Integer healthy;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
