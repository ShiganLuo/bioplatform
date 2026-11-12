package com.bioplatform.common.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 宿主机路径解析工具
 * <p>
 * backend 容器挂载 {@code /:/host:ro}，用户输入的宿主机路径在容器内需补 /host 前缀才能访问。
 * 约定：DB 与接口一律使用用户原样的宿主机路径，访问磁盘时统一经本工具映射；
 * 反向（存量 /host 前缀路径遇到挂载变化）也做兼容。
 *
 * @author luosg
 */
public final class HostPathUtil {

    private static final String HOST_PREFIX = "/host";

    private HostPathUtil() {
    }

    /**
     * 解析为磁盘上实际存在的路径。
     * 依次尝试：原路径 → 补 /host 前缀 → 去 /host 前缀；均不存在则返回原路径（供上层报错）。
     */
    public static Path resolveExisting(String raw) {
        Path p = Paths.get(raw);
        if (Files.exists(p)) {
            return p;
        }
        String stripped = raw.startsWith("/") ? raw.substring(1) : raw;
        Path host = Paths.get(HOST_PREFIX, stripped);
        if (Files.exists(host)) {
            return host;
        }
        if (raw.startsWith(HOST_PREFIX + "/")) {
            Path un = Paths.get(raw.substring(HOST_PREFIX.length()));
            if (Files.exists(un)) {
                return un;
            }
        }
        return p;
    }

    /**
     * 去掉 /host 前缀，得到宿主机视角路径（给 worker 的 docker -v 等宿主机侧语义用）
     */
    public static String toHostPath(String path) {
        if (path != null && path.startsWith(HOST_PREFIX + "/")) {
            return path.substring(HOST_PREFIX.length());
        }
        return path;
    }
}
