# AI Agent 模块技术文档

> BioPlatform AI Agent 完整架构文档，涵盖 Skill 体系、多智能体编排、工具系统、SSE 流式输出。

## 一、架构总览

```
用户消息 → 前端 (AgentView.vue)
    │
    │ POST /api/front/agent/chat/stream (SSE)
    ▼
┌──────────────────────────────────────────────────────┐
│                  AgentServiceImpl                     │
│  ┌────────────────────────────────────────────────┐  │
│  │              SkillRegistry                      │  │
│  │  ┌──────────┐ ┌──────────┐ ┌────────────────┐  │  │
│  │  │ 代码Skill │ │ 数据库Skill│ │ 动态片段(schema)│  │  │
│  │  │ (Spring DI)│ │ (Agent自建)│ │               │  │  │
│  │  └─────┬────┘ └─────┬────┘ └───────┬────────┘  │  │
│  │        └────────────┼──────────────┘            │  │
│  │                     ▼                           │  │
│  │         buildSystemPrompt()                     │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │         AgentOrchestrator (意图路由)            │  │
│  │  ┌─────────┐  ┌──────────┐  ┌───────────────┐  │  │
│  │  │QAAgent  │  │DataAgent │  │PipelineAgent  │  │  │
│  │  └────┬────┘  └────┬─────┘  └──────┬────────┘  │  │
│  │       └────────────┼───────────────┘            │  │
│  └────────────────────┼────────────────────────────┘  │
│                       ▼                              │
│  ┌────────────────────────────────────────────────┐  │
│  │  LLMClient (OpenAI兼容)  ←→  AgentToolExecutor │  │
│  │  工具调用循环 (最多5轮)    ←→  Tool注册表       │  │
│  └────────────────────────────────────────────────┘  │
│                       │                              │
│                       ▼                              │
│              SseEmitter (心跳保活 + 断连检测)         │
└──────────────────────────────────────────────────────┘
    │
    ▼
  前端渲染 (流式文本 + 工具调用卡片 + 状态提示)
```

## 二、Skill 体系

### 设计理念

参照 Hermes Agent 的 skill 架构：**工具自描述，提示词自动生成**。新增工具不再需要手动修改系统提示词。

### 核心接口

```java
public interface Skill {
    String getName();                    // 技能名称
    String getDescription();             // 简介
    String getTriggerDescription();      // 什么时候用
    String getUsageHint();               // 怎么用
    String getSystemPromptFragment();    // 动态上下文（如数据库schema）
    int getPriority();                   // 排序优先级
}
```

### Tool extends Skill

```java
public interface Tool extends Skill {
    Map<String, Object> getParameters();  // JSON Schema 参数定义
    String execute(Map<String, String> args);
    // Skill 方法有默认实现，工具只需覆盖需要的
}
```

每个 Tool 自动成为 Skill，无需额外实现。

### SkillRegistry

自动发现所有 Skill 实现，按优先级拼装系统提示词：

```
系统提示词 = 基础提示
           + 各 Skill 的 trigger/hint 描述
           + 全局规则
           + 各 Skill 的动态片段（如数据库schema）
```

代码 Skill 通过 Spring `List<Skill>` 自动注入，数据库 Skill 从 `skill:*` 配置项加载。

### 当前注册的 Skill

| Skill | 优先级 | 触发条件 | 动态片段 |
|-------|--------|----------|----------|
| database_query | 10 | 用户询问业务数据 | 数据库schema |
| shell_execute | 20 | 文件系统/系统状态 | - |
| file_info | 30 | 查询文件元数据 | - |
| format_info | 40 | 询问文件格式 | - |
| pipeline_list | 30 | 查询分析流程 | - |
| create_skill | 200 | 发现新模式时 | - |

### Agent 自建 Skill

Agent 可通过 `create_skill` 工具创建新 skill，持久化到数据库 `system_config` 表（key 前缀 `skill:`）。下次对话自动加载。

```json
{
  "name": "gene_expression_query",
  "description": "查询基因表达数据",
  "trigger": "当用户询问基因表达、FPKM、TPM等数据时",
  "hint": "查询 data_files 表中 organism 和 genome_version 字段",
  "fragment": "常用查询：SELECT * FROM data_files WHERE organism='Human'"
}
```

## 三、多智能体编排

### 意图识别

正则模式匹配（<1ms），不消耗 LLM 调用：

```java
Map<String, List<String>> INTENT_PATTERNS = Map.of(
    "pipeline",     List.of("流水线", "pipeline", "流程", "workflow"),
    "data_analysis", List.of("VCF", "BAM", "FASTA", "比对", "变异")
);
// 默认兜底 → qa
```

### Agent 分工

| Agent | 职责 | 工具 |
|-------|------|------|
| QAAgent | 通用问答 | 全部工具 |
| DataAnalysisAgent | 数据分析 | file_info, format_info, shell_execute |
| PipelineAgent | 流水线管理 | pipeline_list, file_info, shell_execute |

### 工具调用循环

```
1. 构建消息列表（用户消息 + 历史上下文）
2. SkillRegistry.buildSystemPrompt() 生成系统提示词
3. LLMClient.chatWithTools(messages, systemPrompt, tools)
4. 如果 LLM 返回 tool_calls → 执行工具 → 结果加入消息 → 回到3
5. 最多5轮，轮次耗尽后补一次无工具调用生成文本回复
6. 最终文本回复通过 SSE 流式推送到前端
```

## 四、工具系统

### 工具注册

AgentToolExecutor 通过 Spring `List<Tool>` 自动发现所有 `@Component` 工具：

```java
@Component
public class AgentToolExecutor {
    public AgentToolExecutor(List<Tool> tools) {
        for (Tool tool : tools) {
            toolRegistry.put(tool.getName(), tool);
        }
    }
}
```

### database_query

直接通过 JDBC 查询 MySQL，绕过容器内无 mysql 客户端的限制。

```java
@Component
public class DatabaseQueryTool implements Tool {
    // 注入 Spring DataSource（复用后端连接池）
    private final DataSource dataSource;

    // 安全校验：只允许 SELECT/SHOW/DESCRIBE
    // 拦截 INSERT/UPDATE/DELETE/DROP 等写操作
    // 最多返回100行，输出截断8KB
}
```

### shell_execute

通过 `chroot /host` 在宿主机执行命令：

```
Docker 容器 (backend)
  ├── /:/host:ro  ← 宿主机根目录只读挂载
  └── ShellExecuteTool
       └── chroot /host bash -c "command"
            ▼
       宿主机 (文件系统 + 生信工具)
```

**安全措施：**
- 命令黑名单（rm -rf, DROP TABLE, curl|bash 等）
- 输出截断 10KB
- 超时控制（默认30s，最大300s）
- 只读挂载

**远程执行：** 指定 `worker_id` 时 HTTP 转发到 Worker 节点。

### create_skill

Agent 主动创建新 skill，JSON 持久化到数据库，下次对话自动生效。

## 五、SSE 流式输出

### 连接管理

```java
SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5分钟超时

// 心跳保活：每15秒发送注释行，防止nginx/HTTP2空闲断连
ScheduledExecutorService heartbeat = ...;
heartbeat.scheduleAtFixedRate(() -> {
    emitter.send(SseEmitter.event().comment("keepalive"));
}, 15, 15, TimeUnit.SECONDS);

// 断连检测：客户端断开时中断工具调用循环
AtomicBoolean disconnected = new AtomicBoolean(false);
emitter.onCompletion(() -> disconnected.set(true));
emitter.onTimeout(() -> disconnected.set(true));
emitter.onError(e -> disconnected.set(true));
```

### 事件格式

```
data: {"status":"正在分析问题..."}
data: {"tool_call":{"name":"database_query","arguments":{"sql":"..."}}}
data: {"tool_result":{"name":"database_query","output":"{...}"}}
data: {"delta":"数据库中共有 "}
data: {"delta":"24 个项目"}
data: {"done":true,"conversationId":6}
```

### nginx 配置要求

```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_buffering off;        # SSE必须关闭缓冲
    proxy_read_timeout 300s;    # 工具调用可能耗时较长
}
```

## 六、LLM 客户端

### 配置

存储在数据库 `system_config` 表，运行时动态加载：

| Key | 说明 |
|-----|------|
| llm_api_key | API密钥（AES-GCM加密存储） |
| llm_model | 模型名称 |
| llm_base_url | API地址 |
| llm_provider | 提供商标识 |

### 特性

- **多模型支持**：OpenAI 兼容协议，支持 DeepSeek/MiMo/OpenAI/通义千问等
- **自动重试**：最多3次，指数退避
- **工具调用**：Function Calling 协议，支持并行工具调用
- **加密存储**：API Key 使用 AES-GCM 加密，前端遮蔽显示

## 七、前端交互

### AgentView.vue

```
┌──────────────────────────────────────────┐
│ 对话列表侧边栏  │     聊天区域           │
│  - 新对话       │  - 欢迎页 + 建议问题   │
│  - 对话历史     │  - 消息列表            │
│  - 批量删除     │  - 流式渲染区          │
│                 │  - 工具调用卡片        │
│                 │  - 输入框              │
└──────────────────────────────────────────┘
```

### agentApi.ts

```typescript
// SSE 流式调用（原生 fetch，非 EventSource）
chatStream(data, onToken, onDone, onError, onToolCall, onToolResult, onStatus)

// Token 读取：优先 Pinia store（实时），fallback localStorage
function getAccessToken(): string {
    const store = useUserStore()
    if (store.token) return store.token
    return JSON.parse(localStorage.getItem('bio_user')).token
}

// 401 自动重试：用最新 token 重试一次
function doFetch(retryOn401 = true) { ... }
```

## 八、数据库表

```sql
-- Agent 对话
agent_conversations(id, user_id, project_id, title, model_name, created_at, updated_at)

-- Agent 消息
agent_messages(id, conversation_id, role, content, tool_calls, created_at)

-- Agent 工具配置
agent_tools(id, name, description, enabled, config_json)

-- 系统配置（含 LLM 配置 + Agent Skill）
system_config(id, config_key, config_value, description)
-- skill:* 前缀的为 Agent 自建 Skill
```

## 九、安全设计

| 层面 | 措施 |
|------|------|
| 认证 | JWT Bearer Token，前台可选认证 |
| API Key | AES-GCM 加密存储，前端遮蔽显示 |
| SQL注入 | DatabaseQueryTool 白名单校验（只允许 SELECT/SHOW/DESCRIBE） |
| Shell注入 | ShellExecuteTool 命令黑名单 + 只读挂载 |
| 输出截断 | 数据库查询 8KB，Shell 10KB，防止撑爆 LLM 上下文 |
| SSE保活 | 心跳15秒 + 断连检测 + 连接断开时中断工具调用 |

## 十、部署

```bash
# 本地构建
cd bioplatform-springboot && mvn package -DskipTests

# Docker 镜像（使用预构建 JAR）
docker build -t bioplatform-backend -f Dockerfile.local .

# 传输到远程
docker save bioplatform-backend | ssh aliyun 'docker load'

# 部署
ssh aliyun 'cd ~/bioplatform && docker-compose -f docker-compose-remote.yml up -d --force-recreate backend'
```

### docker-compose 关键配置

```yaml
backend:
  cap_add:
    - SYS_CHROOT          # chroot 需要
  volumes:
    - /:/host:ro           # 宿主机挂载
    - bioplatform-uploads:/app/uploads
  networks:
    - blog_net             # 与 MySQL/Redis 同网络
```
