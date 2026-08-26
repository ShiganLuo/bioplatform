package com.bioplatform.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * 存储策略接口
 * 屏蔽共享存储和分布式存储的差异
 *
 * @author luosg
 */
public interface StorageStrategy {

    /**
     * 存储类型标识
     */
    String getType();

    /**
     * 保存文件
     *
     * @param file      文件内容
     * @param projectId 项目ID
     * @param fileName  文件名
     * @return 存储路径（数据库记录用）
     */
    String store(MultipartFile file, Long projectId, String fileName);

    /**
     * 保存文件（字节数组，分片合并用）
     *
     * @param data      文件字节
     * @param projectId 项目ID
     * @param fileName  文件名
     * @return 存储路径
     */
    String storeBytes(byte[] data, Long projectId, String fileName);

    /**
     * 解析存储路径为可访问的本地路径
     *
     * @param storagePath 存储路径
     * @return 本地文件路径
     */
    Path resolve(String storagePath);

    /**
     * 删除文件
     *
     * @param storagePath 存储路径
     */
    void delete(String storagePath);

    /**
     * 文件是否存在
     *
     * @param storagePath 存储路径
     */
    boolean exists(String storagePath);

    /**
     * 获取文件大小
     *
     * @param storagePath 存储路径
     * @return 文件大小（字节），不存在返回 -1
     */
    long size(String storagePath);
}
