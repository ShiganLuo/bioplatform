package com.bioplatform.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Omics 仓库 git 版本解析(纯文件读,不需要 git 二进制):
 * .git/HEAD → refs 文件 → packed-refs 兜底。backend 经 /host RO 挂载直读。
 *
 * @author luosg
 */
public final class OmicsVersionUtil {

    private static final Logger log = LoggerFactory.getLogger(OmicsVersionUtil.class);

    private OmicsVersionUtil() {
    }

    /**
     * 解析仓库当前 HEAD commit;失败/不完整返回 null(=未知)。
     *
     * @param rootPathString Omics 仓库根(宿主机路径,经 HostPathUtil 映射)
     */
    public static String resolveHead(String rootPathString) {
        if (rootPathString == null || rootPathString.isBlank()) {
            return null;
        }
        try {
            Path root = HostPathUtil.resolveExisting(rootPathString);
            return resolveHead(root);
        } catch (Exception e) {
            log.warn("Omics 版本解析失败(路径映射): root={}, error={}", rootPathString, e.getMessage());
            return null;
        }
    }

    public static String resolveHead(Path root) {
        try {
            Path gitDir = root.resolve(".git");
            if (!Files.isDirectory(gitDir)) {
                return null;
            }
            Path head = gitDir.resolve("HEAD");
            if (!Files.isRegularFile(head)) {
                return null;
            }
            String headContent = Files.readString(head).trim();
            if (!headContent.startsWith("ref:")) {
                return isSha(headContent) ? headContent : null;
            }
            String ref = headContent.substring("ref:".length()).trim();
            // 松散 ref 文件优先
            Path refFile = gitDir.resolve(ref);
            if (Files.isRegularFile(refFile)) {
                String sha = Files.readString(refFile).trim();
                return isSha(sha) ? sha : null;
            }
            // packed-refs 兜底: "<sha> <ref>" 行
            Path packed = gitDir.resolve("packed-refs");
            if (Files.isRegularFile(packed)) {
                List<String> lines = Files.readAllLines(packed);
                for (String line : lines) {
                    if (line.startsWith("#") || line.startsWith("^") || line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2 && parts[1].equals(ref) && isSha(parts[0])) {
                        return parts[0];
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Omics 版本解析失败: root={}, error={}", root, e.getMessage());
            return null;
        }
    }

    private static boolean isSha(String s) {
        return s != null && s.matches("[0-9a-f]{7,40}");
    }
}
