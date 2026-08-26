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

### SSE vs WebSocket

**LLM 对话场景的本质是：用户发一条消息，服务端持续推送 token。** 这是典型的单向流式推送，不需要双向通信。

| 对比 | SSE | WebSocket |
|------|-----|-----------|
| 通信模型 | 单向（服务端 → 客户端） | 双向 |
| 协议 | 普通 HTTP | 独立的 ws:// 协议 |
| 自动重连 | 浏览器内置支持 | 需要手动实现 |
| 负载均衡 | HTTP 层天然支持 | 需要 sticky session |
| 防火墙/代理 | 无阻碍 | 可能被拦截 |
| 实现复杂度 | 低（Spring SseEmitter） | 高（需要管理连接状态） |
| 认证 | 复用 HTTP 头（Authorization） | 需要在 URL 传 token 或单独握手 |
| Nginx 配置 | `proxy_buffering off` 即可 | 需要 Upgrade 头转发 |

### 为什么不用 WebSocket？

1. **LLM 对话不需要双向通信** — 用户发消息用普通 POST，服务端推送 token 用 SSE，职责清晰
2. **WebSocket 连接管理复杂** — 需要心跳保活、断线重连、连接状态管理，SSE 浏览器自动重连
3. **HTTP 代理兼容性** — SSE 走普通 HTTP，企业防火墙和反向代理无阻碍
4. **认证简单** — SSE 请求复用 HTTP 的 Authorization header，WebSocket 需要在 URL 传 token 或做单独握手

### 什么时候该用 WebSocket？

- 双向实时通信（聊天室、协同编辑）
- 高频双向消息（游戏、股票行情）
- 需要二进制传输

**总结：SSE 是 LLM 流式输出的最佳选择，因为它天然匹配"单向持续推送"的场景，实现简单、兼容性好、无需额外连接管理。**

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
