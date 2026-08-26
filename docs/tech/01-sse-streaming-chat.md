# Spring Boot + Vue 3 实现 SSE 流式 AI 对话

> BioPlatform 技术文档：SSE 流式对话的完整实现方案。

## 背景

传统 HTTP 请求是"请求-等待-响应"模式，用户发送消息后需要等待 LLM 完整生成回复。对于大语言模型这种生成式场景，一个回复可能需要 5-30 秒，用户体验很差。

**Server-Sent Events (SSE)** 是一种服务端向客户端单向推送的技术，相比 WebSocket 更轻量，天然适合 LLM 流式输出场景。

## 为什么选 SSE？

消息推送方案有很多，为什么不用其他的？

| 方案 | 通信方向 | 协议开销 | 适用场景 | LLM 流式适合度 |
|------|---------|---------|---------|--------------|
| **SSE** | 服务端 → 客户端（单向） | 普通 HTTP | 服务端推送、通知、流式数据 | ★★★★★ |
| **WebSocket** | 双向 | 较高（帧头） | 聊天室、协同编辑、游戏 | ★★★☆☆ |
| **长轮询** | 伪推送 | 高（反复建连） | 兼容性要求高的旧系统 | ★★☆☆☆ |
| **短轮询** | 客户端轮询 | 最高 | 简单场景 | ★☆☆☆☆ |

## SSE vs WebSocket 详细对比

这是最常被问到的问题，单独展开对比。

### 1. 通信模型

```
SSE（单向）：
  客户端 ──── POST 消息 ────→ 服务端
  客户端 ←── SSE 流式推送 ── 服务端

WebSocket（双向）：
  客户端 ←───→ 双向实时通信 ←───→ 服务端
```

LLM 对话的通信模式是：**用户发一条消息，服务端持续推送 token。** 这是典型的"请求-流式响应"模式，SSE 的单向模型完美匹配。WebSocket 的双向能力在这个场景中完全用不到——用户不需要在 AI 生成回复的过程中持续发送消息。

### 2. 协议层

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| 传输协议 | HTTP/1.1 或 HTTP/2 | 独立的 ws:// 协议 |
| 数据格式 | 纯文本（`data:` 行） | 文本帧或二进制帧 |
| 帧开销 | 无额外帧头 | 每帧 2-14 字节帧头 |
| HTTP/2 多路复用 | 支持（多个 SSE 流共享连接） | 不支持 |

SSE 基于 HTTP，和普通 API 请求走同一条连接，不需要协议升级。WebSocket 需要一次 HTTP Upgrade 握手后切换到独立协议。

### 3. 连接管理

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| 建立连接 | 普通 HTTP 请求 | HTTP Upgrade 握手 |
| 自动重连 | **浏览器原生支持**（EventSource） | 需要手动实现重连逻辑 |
| 心跳保活 | 不需要（HTTP 长连接） | 需要实现 ping/pong 心跳 |
| 断线检测 | HTTP 层自动检测 | 需要心跳超时检测 |
| 连接状态管理 | 无（每次请求独立） | 需要维护连接状态 |

这是 SSE 最大的优势之一。浏览器的 `EventSource` API 内置自动重连，而 WebSocket 需要自己写重连逻辑、指数退避、心跳保活——前面我们在在线客服模块中就踩了 WebSocket 重连死循环的坑。

### 4. 认证

```typescript
// SSE：复用 HTTP 头，和普通 API 一样
fetch('/api/chat/stream', {
  headers: { 'Authorization': `Bearer ${token}` }
})

// WebSocket：认证是个问题
// 方案1：URL 传 token（不安全，token 暴露在 URL 中）
new WebSocket('ws://host/ws?token=xxx')
// 方案2：单独握手（多一次 HTTP 请求）
// 方案3：Cookie（有 CSRF 风险）
```

SSE 请求和普通 HTTP 请求完全一样，认证方式统一。WebSocket 的认证需要额外处理，且 URL 传 token 有安全风险（token 会出现在服务器日志、代理日志中）。

### 5. 负载均衡与代理

| 维度 | SSE | WebSocket |
|------|-----|-----------|
| Nginx | `proxy_buffering off` | 需要 `proxy_set_header Upgrade` |
| 负载均衡 | HTTP 层天然支持 | 需要 sticky session |
| CDN | 支持 | 不支持 |
| 防火墙 | 无阻碍（就是 HTTP） | 可能被企业防火墙拦截 |

SSE 走普通 HTTP，所有 HTTP 基础设施（负载均衡器、CDN、WAF）都天然支持。WebSocket 需要基础设施显式支持协议升级，企业环境中可能被防火墙拦截。

### 6. 后端实现

```java
// SSE：Spring Boot 原生支持，3 行代码
@GetMapping("/stream")
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(300_000L);
    // emitter.send("data: {...}") 即可推送
    return emitter;
}

// WebSocket：需要 Handler、握手拦截、连接管理
@Component
public class MyWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);  // 管理连接
    }
    // 需要处理心跳、断线、异常...
}
```

### 7. 前端实现

```typescript
// SSE 方式：fetch + ReadableStream
const response = await fetch('/api/chat/stream', { method: 'POST', body })
const reader = response.body.getReader()
while (true) {
  const { done, value } = await reader.read()
  if (done) break
  // 解析 data: 行
}

// WebSocket 方式
const ws = new WebSocket('ws://host/ws')
ws.onmessage = (e) => { /* 处理消息 */ }
ws.onclose = () => { /* 手动重连 */ }
```

### 什么时候该用 WebSocket？

- **双向实时通信**：聊天室、协同编辑、多人游戏
- **高频双向消息**：股票行情、实时竞价
- **二进制传输**：音视频流、文件传输
- **需要服务端主动推送的场景**：通知系统（但 SSE 也可以）

### 本项目的选择

BioPlatform 中有两个实时场景，分别选择了不同的方案：

| 场景 | 方案 | 原因 |
|------|------|------|
| AI 对话流式输出 | **SSE** | 单向推送，请求-响应模式 |
| 在线客服聊天 | **WebSocket** | 双向实时通信，用户和客服互发消息 |

**总结：SSE 是 LLM 流式输出的最佳选择，因为它天然匹配"单向持续推送"的场景，实现简单、兼容性好、无需额外连接管理。但如果是双向实时通信场景（如聊天室），WebSocket 仍然是正确的选择。**

## 整体架构

```
前端 fetch POST
    │
    ▼
后端 SseEmitter（5分钟超时）
    │
    ├─ 1. 保存用户消息到 DB
    ├─ 2. 获取历史上下文（最近20条）
    ├─ 3. OkHttp 流式调用 LLM API（stream: true）
    │     ├─ 逐 token 解析 SSE data: 行
    │     ├─ 过滤 null delta
    │     └─ emitter.send({"delta":"token"})
    ├─ 4. 流结束：保存完整助手回复到 DB
    └─ 5. emitter.send({"done":true,"conversationId":N})
```

## 后端实现

### SseEmitter 创建

```java
@PostMapping("/chat/stream")
public SseEmitter chatStream(@RequestBody Map<String, Object> params) {
    Long userId = LoginUserHolder.getCurrentUserId();
    if (userId == null) {
        SseEmitter errEmitter = new SseEmitter();
        try {
            errEmitter.send(SseEmitter.event().data("{\"error\":\"请先登录\"}"));
            errEmitter.complete();
        } catch (Exception ignored) {}
        return errEmitter;
    }
    return agentService.streamChat(conversationId, content, userId);
}
```

### OkHttp 流式调用 LLM

核心在于使用 OkHttp 的异步调用 + `ResponseBody.charStream()` 逐行读取：

```java
ObjectNode requestNode = objectMapper.createObjectNode();
requestNode.put("model", config.model());
requestNode.put("stream", true);  // 关键：启用流式

Request request = new Request.Builder()
    .url(config.baseUrl() + "/chat/completions")
    .addHeader("Authorization", "Bearer " + config.apiKey())
    .post(RequestBody.create(
        objectMapper.writeValueAsString(requestNode),
        MediaType.parse("application/json")))
    .build();

httpClient.newCall(request).enqueue(new Callback() {
    @Override
    public void onResponse(Call call, Response response) {
        try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String json = line.substring(5).trim();
                if ("[DONE]".equals(json)) break;

                JsonNode node = objectMapper.readTree(json);
                JsonNode delta = node.at("/choices/0/delta/content");

                if (!delta.isMissingNode() && !delta.isNull()) {
                    String token = delta.asText();
                    emitter.send(SseEmitter.event().data("{\"delta\":\"" + escape(token) + "\"}"));
                }
            }
            emitter.send(SseEmitter.event().data("{\"done\":true}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
});
```

### SecurityContext 跨线程传播

Spring Security 的 `SecurityContext` 默认存储在 `ThreadLocal` 中。当使用 `SseEmitter` 时，响应在 Tomcat 的 async 线程池中执行，`ThreadLocal` 丢失。

解决方案：将 `SecurityContext` 存入 `Request` 的 attribute 中：

```java
.securityContext(sc -> sc
    .securityContextRepository(new RequestAttributeSecurityContextRepository())
)
```

## 前端实现

### 为什么不用 Axios？

项目中普通 API 请求用 Axios，但 SSE 流式场景**必须用原生 fetch**，原因：

| 对比 | Axios | fetch |
|------|-------|-------|
| 流式读取 | 不支持 `ReadableStream`，需要等响应完全返回 | 原生支持 `response.body.getReader()` |
| POST + 流式 | Axios 底层用 XMLHttpRequest，无法逐 chunk 读取 | fetch 原生支持 |
| 中断 | `CancelToken`（已废弃）或 `AbortController` | 原生 `AbortController` |
| 响应类型 | 默认解析 JSON，流式场景会阻塞 | 默认流式读取 |

Axios 的 `responseType: 'stream'` 仅在 Node.js 环境有效（Node.js 的 http 模块支持），浏览器端不生效。所以 SSE 流式对话**必须用 fetch**。

### fetch + ReadableStream 解析 SSE

```typescript
fetch('/api/front/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify(data),
    signal: abortController.signal,
}).then(async (response) => {
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
            if (!line.startsWith('data:')) continue
            const jsonStr = line.slice(5).trim()
            if (!jsonStr) continue
            const obj = JSON.parse(jsonStr)
            if (obj.delta) { onToken(obj.delta) }
            if (obj.done) { onDone({ conversationId: obj.conversationId }); return }
        }
    }
})
```

### Vue 3 响应式流式渲染

**踩坑点**：不要 push 空消息再 += 填充，要用独立的 `streamingContent` ref。

```vue
<script setup>
const messages = ref([])
const streamingContent = ref('')  // 独立的流式内容 ref

function sendMessage(text) {
  messages.value.push({ role: 'user', content: text })
  streamingContent.value = ''

  chatStream(
    { message: text },
    (token) => { streamingContent.value += token; scrollToBottom() },
    (info) => {
      messages.value.push({ role: 'assistant', content: streamingContent.value })
      streamingContent.value = ''
    }
  )
}
</script>

<template>
  <ChatMessage v-for="msg in messages" :message="msg" />
  <div v-if="streamingContent" class="message assistant">
    <div v-html="renderMarkdown(streamingContent)"></div>
  </div>
</template>
```

## 踩坑总结

| 问题 | 原因 | 解决 |
|------|------|------|
| data: 后有空格解析失败 | SSE 规范不一致 | 用 slice(5) + trim() |
| LLM 返回 "null" 字符串 | delta.content 为 null | 后端 isNull() 检查 |
| 前端消息不更新 | messages[idx].content += 不触发 DOM 更新 | 独立 streamingContent ref |
| SecurityContext 丢失 | async 线程 ThreadLocal 为空 | RequestAttributeSecurityContextRepository |
| Nginx 缓冲 SSE | 默认 proxy_buffering on | 设置 proxy_buffering off |
