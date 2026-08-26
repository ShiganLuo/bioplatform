# 多智能体编排：意图识别 + 工具调用 + 流式输出

> BioPlatform 技术文档：AI Agent 模块的编排器模式设计。

## 架构概览

```
用户消息
    │
    ▼
┌─────────────────────────────────┐
│      AgentOrchestrator          │  ← 编排器
│  ┌───────────────────────────┐  │
│  │    意图识别引擎            │  │  ← 正则模式匹配，<1ms
│  └─────────────┬─────────────┘  │
│      ┌─────────┼─────────┐     │
│      ▼         ▼         ▼     │
│  ┌───────┐ ┌───────┐ ┌───────┐ │
│  │ Data  │ │ Pipe- │ │  QA   │ │  ← 专业智能体
│  │Analy- │ │ line  │ │ Agent │ │
│  │sis    │ │ Agent │ │       │ │
│  └───────┘ └───────┘ └───────┘ │
│  ┌───────────────────────────┐  │
│  │  LLMClient + ToolExecutor │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘
    │
    ▼
  流式回复 → SSE → 前端渲染 → 持久化
```

## 一、意图识别引擎

### 为什么用正则而不是 LLM？

| 方案 | 延迟 | 成本 | 准确性 |
|------|------|------|--------|
| LLM 意图分类 | 1-3秒 | 每次调用计费 | 高 |
| 正则模式匹配 | <1ms | 零成本 | 中等（够用） |

### 实现

```java
@Component
public class AgentOrchestrator {

    private static final Map<String, List<String>> INTENT_PATTERNS = Map.of(
        "pipeline", List.of("流水线", "pipeline", "流程", "workflow", "运行", "执行"),
        "data_analysis", List.of("VCF", "BAM", "FASTA", "FASTQ", "BED", "比对", "变异", "QC")
    );

    public String identifyIntent(String userMessage) {
        String lower = userMessage.toLowerCase();
        for (var entry : INTENT_PATTERNS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword.toLowerCase())) return entry.getKey();
            }
        }
        return "qa";  // 默认兜底
    }
}
```

## 二、工具调用链

### 工具定义

```java
@Component
public class AgentToolExecutor {
    @PostConstruct
    public void registerTools() {
        tools.put("file_info", new FileInfoTool(dataFileMapper));
        tools.put("format_info", new FormatInfoTool());
        tools.put("pipeline_list", new PipelineSearchTool(pipelineMapper));
    }

    public String executeTool(String toolName, Map<String, Object> args) {
        Tool tool = tools.get(toolName);
        if (tool == null) return "Unknown tool: " + toolName;
        return tool.execute(args);
    }
}
```

### LLM 工具调用循环

```
1. 发送消息 + 工具定义给 LLM
2. LLM 返回 tool_calls
3. 后端执行工具，获取结果
4. 将工具结果作为 tool message 发回 LLM
5. LLM 基于工具结果生成最终回复
```

```java
while (true) {
    LLMResponse response = llmClient.chatWithTools(messages, systemPrompt, toolDefinitions);
    if (response.hasToolCalls()) {
        for (ToolCall call : response.toolCalls()) {
            String result = toolExecutor.executeTool(call.name(), call.arguments());
            messages.add(toolResultMessage(call.id(), result));
        }
        continue;  // 继续对话
    }
    return response.content();  // 最终文本回复
}
```

## 三、LLM 客户端

### 多模型支持

配置存储在数据库，运行时动态加载：

```java
private LLMConfig loadConfig() {
    String apiKey = getConfigValue("llm_api_key");
    String model = getConfigValue("llm_model");
    String baseUrl = getConfigValue("llm_base_url");
    return new LLMConfig(apiKey, model, baseUrl);
}
```

### SSE 流式调用

```java
httpClient.newCall(httpRequest).enqueue(new Callback() {
    @Override
    public void onResponse(Call call, Response response) {
        try (BufferedReader reader = new BufferedReader(response.body().charStream())) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String json = line.substring(5).trim();
                if ("[DONE]".equals(json)) break;

                JsonNode delta = objectMapper.readTree(json).at("/choices/0/delta/content");
                if (!delta.isMissingNode() && !delta.isNull()) {
                    emitter.send(SseEmitter.event().data("{\"delta\":\"" + delta.asText() + "\"}"));
                }
            }
            emitter.send(SseEmitter.event().data("{\"done\":true}"));
            emitter.complete();
        }
    }
});
```

## 四、上下文管理

```java
// 获取最近 20 条消息作为上下文
List<AgentMessage> history = messageMapper.selectByConversationId(conversationId, 20);
```

## 踩坑总结

| 问题 | 解决 |
|------|------|
| 工具调用参数解析失败 | LLM 返回 JSON 可能格式不一致，加 try-catch |
| delta.content 为 null | Jackson 序列化为 "null" 字符串，需 isNull() 检查 |
| 对话标题为空 | 首条消息截取前20字，不发 LLM |
| 上下文过长 | 限制最近 20 条，避免超出 token 限制 |
