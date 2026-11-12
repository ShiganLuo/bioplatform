package com.bioplatform.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性下载票据
 * <p>
 * 浏览器原生下载需要直接向返回 Content-Disposition: attachment 的 URL 发起请求，
 * 而 SPA 的 JWT 放在 Authorization 头里、&lt;a href&gt; 带不上。故下载前先用已认证请求
 * 换取短时（10 分钟）一次性票据，再以票据 URL 触发浏览器原生下载（有下载进度与保存步骤）。
 * 票据即凭证：单次消费、过期作废、内存存储（重启失效可接受）。
 *
 * @author luosg
 */
@Service
public class DownloadTicketService {

    private static final long TTL_MS = 10 * 60 * 1000L;

    /** type: file / excel / ppt / batch / all */
    public record Ticket(String type, Long fileId, Long projectId, List<String> filePaths, long createdAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issue(Ticket ticket) {
        purge();
        String id = UUID.randomUUID().toString();
        tickets.put(id, ticket);
        return id;
    }

    /** 单次消费：取出即删；不存在或已过期返回 null */
    public Ticket consume(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            return null;
        }
        Ticket t = tickets.remove(ticketId);
        if (t == null) {
            return null;
        }
        if (System.currentTimeMillis() - t.createdAt() > TTL_MS) {
            return null;
        }
        return t;
    }

    private void purge() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
    }
}
