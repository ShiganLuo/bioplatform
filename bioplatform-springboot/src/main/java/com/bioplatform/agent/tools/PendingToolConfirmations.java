package com.bioplatform.agent.tools;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高危工具人审闸门的待确认注册表（2026-11-24 入侵面收敛二期）。
 * <p>
 * requiredAuthority 非空的工具（如 shell_execute）执行前必须经用户确认：
 * 流线程 create() 拿到 Entry 后挂起在 future 上，SSE 推 tool_confirm 事件；
 * 用户经 /api/admin/agent/tools/confirm 回调 resolve() 完成 future。
 * 拒绝/超时均不执行；Entry 由调用方 finally cleanup() 摘除。
 *
 * @author luosg
 */
@Component
public class PendingToolConfirmations {

    /** 单条确认的最长等待时间(毫秒);超过即视为拒绝(SseEmitter 5 分钟超时前收尾) */
    public static final long CONFIRM_TIMEOUT_MS = 3 * 60 * 1000L;

    public record Entry(String id, Long userId, String toolName, Map<String, String> args,
                        CompletableFuture<Boolean> future) {
    }

    private final Map<String, Entry> pending = new ConcurrentHashMap<>();

    public Entry create(Long userId, String toolName, Map<String, String> args) {
        String id = UUID.randomUUID().toString();
        Entry e = new Entry(id, userId, toolName, args, new CompletableFuture<>());
        pending.put(id, e);
        return e;
    }

    /**
     * 用户确认/拒绝。confirmId 不存在或不属于该用户 → false(调用方报"不存在或已过期")。
     */
    public boolean resolve(String confirmId, Long userId, boolean approved) {
        Entry e = pending.get(confirmId);
        if (e == null) {
            return false;
        }
        if (e.userId() != null && userId != null && !e.userId().equals(userId)) {
            return false; // 不允许替他人确认
        }
        e.future().complete(approved);
        return true;
    }

    public void cleanup(String confirmId) {
        pending.remove(confirmId);
    }
}
