package com.bioplatform.dto.datafile;

import lombok.Data;

/**
 * 存储空间信息
 */
@Data
public class StorageInfo {
    /** 磁盘总空间（字节） */
    private long diskTotal;
    /** 磁盘已用空间（字节） */
    private long diskUsed;
    /** 磁盘可用空间（字节） */
    private long diskFree;

    /** 用户上传配额（字节） */
    private long userQuota;
    /** 用户已用空间（字节） */
    private long userUsed;
    /** 用户剩余配额（字节） */
    private long userRemaining;

    /** 本次待上传文件总大小（字节） */
    private long pendingSize;
    /** 是否允许上传 */
    private boolean canUpload;
    /** 不可上传原因 */
    private String reason;
}
