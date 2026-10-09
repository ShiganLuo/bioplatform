# Spring Boot + Vue 3 实现 SSE 流式 AI 对话

> BioPlatform 技术文档：SSE 流式对话的完整实现方案。

## 背景

传统 HTTP 请求是"请求-等待-响应"模式，用户发送消息后需要等待 LLM 完整生成回复。对于大语言模型这种生成式场景，一个回复可能需要 5-30 秒，用户体验很差。

**Server-Sent Events (SSE)** 是一种服务端向客户端单向推送的技术，相比 WebSocket 更轻量，天然适合 LLM 流式输出场景。

## 为什么选 SSE？

LLM 对话的通信模式是：**用户发一条消息，服务端持续推送 token。** 这是典型的"请求-流式响应"，单向推送，不需要双向通信。

SSE 相比其他方案的优势：
- **协议简单** — 基于普通 HTTP，不需要协议升级，所有 HTTP 基础设施天然支持
- **认证统一** — 复用 HTTP Authorization header，不需要在 URL 传 token
- **实现轻量** — Spring Boot 原生 `SseEmitter`，前端 `fetch + ReadableStream`
- **无连接管理** — 不需要心跳保活、断线重连（浏览器自动处理）

> 注：本项目的在线客服模块用的是 WebSocket，因为客服场景需要用户和客服双向实时通信。不同场景选不同方案。

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

#### 问题本质（因果链）

Spring Security 的 `SecurityContext` 存在 `ThreadLocal` 里——**身份跟着线程走**，线程一换就丢。SSE 场景下会遇到两次"换线程"，推导链条如下：

1. **SSE 不能占着容器线程慢慢写** → 必须异步：Controller 返回 `SseEmitter` 时进入异步模式，线程归还容器，连接保留
2. **处理被拆成两段** → 第一段（进 Controller 前的鉴权 + Controller 逻辑）在返回时结束；收尾工作（交付 DeferredResult、拦截器 `afterCompletion`、关闭连接）还没做
3. **收尾必须跑在容器管线里** → 业务线程调 `emitter.complete()` 触发一次 **ASYNC 分发**：容器把同一个 request 对象重新送进管线转**第二圈**（`complete()` 的 javadoc 原文："performing a dispatch into the servlet container, where Spring MVC is invoked once more"）
4. **管线没有旁路，第二圈必然再过一遍过滤器** → 但第二圈跑在另一条线程上，ThreadLocal 是空的，授权过滤器 `anyRequest().authenticated()` 会把这次分发判成匿名 → 401
5. **所以第一圈结束前要把身份存到跨线程不变的载体上，第二圈取回来** → 这就是"跨线程传播"要解决的全部问题

另外，`AgentServiceImpl` 的流式输出是自己 `new Thread` 起的线程，**从不经过过滤器链**，request attribute 机制够不着，需要第二套手动传播。

> 澄清两个常见误解：
> - **第二圈不是新请求**：同一个 `HttpServletRequest` 对象、同一条 TCP 连接，浏览器毫不知情。过滤器的执行单位是"分发"而不是"HTTP 请求"（Servlet 规范的 `DispatcherType` 就是为此定义的）。
> - **token 不会被验两次**：只有第一圈验 token；第二圈只"恢复已验好的结果"。

#### 机制一：request attribute（框架自动，覆盖 ASYNC 分发）

配置（`config/SecurityConfig.java:133-136`）——即"换了身份的存放载体"：

```java
// SecurityContext 存入 request attribute，支持 SSE async dispatch
.securityContext(sc -> sc
    .securityContextRepository(new RequestAttributeSecurityContextRepository())
)
```

完整生命周期（发送 → 载体 → 接收）：

| 环节 | 执行者 | 时机 |
|------|--------|------|
| 写入 ThreadLocal | `SecurityContextHolderFilter`（过滤器链第一圈开头） | 每次分发开始，从仓库 load |
| 认证 | `JwtAuthenticationFilter.java:76` | 第一圈，解析 Bearer token 后 `setAuthentication` |
| **保存到 request attribute** | `SessionManagementFilter` 源码 113 行 `saveContext(...)`（框架自动，"eagerly save... for any possible re-entrant requests"，SEC-1396） | 第一圈，检测到"已认证但仓库里还没有" |
| **从 attribute 恢复到新线程** | `SecurityContextHolderFilter.loadDeferredContext(request)` | 第二圈（ASYNC 分发），同一个 request 对象上读回 |

关键分工，第二圈两个过滤器一跳一不跳：

- `JwtAuthenticationFilter` **跳过**——它是 `OncePerRequestFilter`，默认 `shouldNotFilterAsyncDispatch() = true`，且第二圈没有新 token 可验
- `SecurityContextHolderFilter` **参与**——它的 `FILTER_APPLIED` 标记在第一圈 finally 里已被移除，第二圈重新执行 load，把身份灌回 ThreadLocal

支撑条件（已从本地 m2 的 jar 反编译核实）：Spring Boot 默认把 security 过滤器注册到**全部** DispatcherType（`SecurityProperties$Filter` 构造器里 `EnumSet.allOf(DispatcherType.class)`），所以 ASYNC 分发时过滤链会转第二圈；载体只在本次请求内有效，下一个新请求拿不到（`RequestAttributeSecurityContextRepository` javadoc 明确："It will not be available on subsequent requests"），新请求由 token 重新认证。

#### 机制二：手动传播（`AgentServiceImpl` 自建线程）

`service/impl/AgentServiceImpl.java:209-264`——线程内工作不经过过滤器链，手动三步：

```java
// 发送：起线程前在请求线程捕获（:210）
SecurityContext securityContext = SecurityContextHolder.getContext();

new Thread(() -> {
    // 接收：新线程第一行注入（:214）
    SecurityContextHolder.setContext(securityContext);
    try {
        // processWithTools：工具调用循环 + 流式输出
    } finally {
        // 收尾：清理，防线程残留（:262）
        SecurityContextHolder.clearContext();
    }
}, "sse-stream-" + conversationId).start();
```

注意 lambda 只是闭包引用，**ThreadLocal 本身不跟着线程走**，必须在新线程体内显式 `setContext`，不能靠捕获变量自动生效。

**消费现状（诚实记录）**：全仓库 grep，`SecurityContextHolder` / `OwnershipUtils` 的读取点只在 Controller、`JwtAuthenticationFilter`、`OperLogAspect` 里——`agent/tools` 包一处都没有。即流线程内目前**没有下游读者**，这处传播是防御性储备（将来工具链若加按用户过滤/归属校验，身份已在场）。

**已知边界**：`OwnershipUtils` 是两条腿——`isAdmin()` 读 SecurityContext（已传播，可用），`getCurrentUserId()` 读 `LoginUserHolder`（**未传播**，JwtAuthenticationFilter 只在请求线程设置并清理，新线程恒为 null）。将来若在流线程调 `checkOwnership`，普通用户会因 `currentUserId == null` 被一律拒绝（`OwnershipUtils.java:49`）。给工具加身份依赖时，`LoginUserHolder` 需要一并传播。

#### 身份校验发生在哪里

| 时机 | 线程 | 校验点 | 身份传递方式 |
|------|------|--------|--------------|
| 开流前 | 请求线程 | ① 过滤器链 `anyRequest().authenticated()`（否则 401）② `FrontAgentController.java:67` 判"请先登录" ③ `userId` 作为**方法参数**传入 `streamChat` | ThreadLocal → 参数 |
| 流式阶段 | `sse-stream-*` 线程 | 当前无（工具链/DB/LLM 调用都不读身份） | 参数已固定，不再依赖身份 |

对照：心跳线程（`sse-heartbeat-*`）**不传播**——只发 `keepalive` 注释行，没有读者。传播是"有读者才需要"。

#### 两套机制对照

| | 载体 | 发送时机 | 接收时机 | 当前有无实际消费 |
|---|---|---|---|---|
| request attribute | HttpServletRequest 属性 | 认证后（SessionManagementFilter 自动） | 同请求的 ASYNC 分发重入时（SecurityContextHolderFilter） | 有（授权过滤器） |
| 手动 setContext | 局部变量传引用 | `new Thread` 之前（:210） | 新线程第一行（:214） | 暂无（防御性） |

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
| SecurityContext 丢失 | 第二圈 ASYNC 分发跑在新线程，ThreadLocal 为空 | request attribute 自动恢复（配置层）+ 自建线程手动 `setContext`（见上文两套机制） |
| 流线程内归属校验失败（隐患） | `LoginUserHolder` 未随 SecurityContext 传播 | 在流线程补充 `LoginUserHolder.setCurrentUser(...)`，或改用参数传递 |
| Nginx 缓冲 SSE | 默认 proxy_buffering on | 设置 proxy_buffering off |
