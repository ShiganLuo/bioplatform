# Agent Shell 工具能力设计文档

> 核心目标：让 AI Agent 能直接调用宿主机上的 shell 工具访问业务数据。

## 一、架构设计

### 核心约束

- 后端服务（bioplatform-springboot）运行在 Docker 容器内
- 业务数据和生信工具（samtools、mysql 等）在宿主机上
- 容器内不能直接访问宿主机的文件系统和工具

### 解决方案：chroot /host

```
┌─────────────────────────────────────────────────┐
│ Docker 容器 (bioplatform-backend)                │
│                                                  │
│  Agent → ShellExecuteTool                        │
│       → ProcessBuilder("chroot", "/host",        │
│                         "bash", "-c", command)   │
│                                                  │
│  挂载: /:/host:ro                                │
└────────────────────┬────────────────────────────-┘
                     │ chroot /host
                     ▼
┌─────────────────────────────────────────────────┐
│ 宿主机 (aliyun)                                   │
│                                                  │
│  /bin, /usr/bin    → bash, ls, find, df, mysql   │
│  /usr/local/bin    → samtools, bcftools, ...      │
│  /data             → 业务数据 (或 NFS 挂载点)      │
│  /etc              → 配置文件                      │
└─────────────────────────────────────────────────-┘
```

### 两种部署场景

#### 场景 1：单机 + 宿主机执行

Agent 的 shell 命令通过 `chroot /host` 在后端所在宿主机上执行。

```
docker-compose-remote.yml:
  backend:
    volumes:
      - /:/host:ro          # 宿主机根目录挂载到容器 /host
```

#### 场景 2：多节点 + 远程 Worker 执行（NFS / 跨网络）

Agent 可指定 `worker_id` 将命令转发到远程 Worker 节点。Worker 节点在宿主机上运行，可访问本地工具和 NFS 共享存储。

```
docker-compose-remote.yml:
  backend:
    volumes:
      - /:/host:ro          # 本地宿主机也挂载，支持本地执行

远程 Worker 通过以下方式连接到 backend：
  - 同局域网：backend 直接 HTTP 调 Worker
  - 跨网络：Worker 通过 SSH 反向隧道连到公网服务器
```

### 与旧方案的对比

| 对比项 | 旧方案（纯 Worker 转发） | 新方案（chroot /host） |
|--------|------------------------|----------------------|
| 本地执行 | 需要单独部署 Worker 进程 | 不需要，chroot 直接执行 |
| 远程执行 | Worker 转发 | 保留，通过 worker_id 指定 |
| 容器改动 | 需要在容器装工具 | 不需要，用宿主机的工具 |
| 部署复杂度 | 高（需额外 Worker 服务） | 低（只加一个 volume 挂载） |

## 二、ShellExecuteTool 工具定义

```
工具名: shell_execute
描述: 在服务器上执行 shell 命令并返回结果。
     默认在当前宿主机上执行（通过 chroot /host），
     指定 worker_id 可在远程计算节点上执行。

参数:
  command   (string, 必需) — 要执行的 shell 命令
  timeout   (integer, 可选) — 超时秒数，默认30，最大300
  workdir   (string, 可选) — 工作目录
  worker_id (string, 可选) — 目标 Worker 节点 ID，不指定则在宿主机执行

执行路由:
  worker_id 为空 → chroot /host bash -c "command"（宿主机执行）
  worker_id 指定 → HTTP POST 到 Worker 的 /worker/shell/execute（远程执行）
```

## 三、安全设计

### 命令黑名单

拦截危险操作，包括：
- 文件系统破坏：`rm -rf`、`mkfs`、`dd if=`、`> /dev/sd`
- 系统控制：`shutdown`、`reboot`
- 数据库破坏：`DROP DATABASE`、`DROP TABLE`、`DELETE FROM`、`TRUNCATE`
- 远程代码执行：`curl ... | bash`、`wget ... | bash`
- Fork 炸弹：`:(){ :|:& };:`

### 其他安全措施

- 输出截断：超过 10KB 截断，防止撑爆 LLM 上下文
- 超时控制：默认 30 秒，最大 300 秒
- 只读挂载：`/:/host:ro`，容器不能写宿主机文件系统
- 数据库查询建议使用 SELECT，黑名单已拦截写操作

## 四、Worker 远程执行

### Worker 端点

```
POST /worker/shell/execute
Body: {"command": "...", "timeout": 30, "workdir": "/data"}
Response: {"exit_code": 0, "success": true, "output": "..."}
```

### WorkerClient 调用

```java
// 选择 Worker
WorkerInfo worker = workerRegistry.getById(workerId);

// 调用 Worker 的 shell 端点
Map<String, Object> result = workerClient.executeShell(
    worker.getUrl(), command, timeout, workdir);
```

### Worker 连接方式

| 网络场景 | 连接方式 | WorkerRegistry 数据来源 |
|----------|----------|----------------------|
| 同局域网 | backend 直接 HTTP 调 Worker | compute_nodes 表 + 健康检查 |
| 跨网络 | Worker 通过 SSH 反向隧道 | 隧道注册 + 健康检查 |

## 五、Agent 工具分配

| Agent | 工具 |
|-------|------|
| QAAgent | pipeline_list, file_info, format_info, **shell_execute**（全部） |
| DataAnalysisAgent | file_info, format_info, **shell_execute** |
| PipelineAgent | pipeline_list, file_info, **shell_execute** |

## 六、文件变更清单

### 修改文件

```
bioplatform-springboot/.../agent/tools/impl/ShellExecuteTool.java
  - chroot /host 宿主机执行
  - worker_id 远程 Worker 转发
  - 命令安全校验 + 输出截断

bioplatform-springboot/.../worker/WorkerClient.java
  - 新增 executeShell() 方法

bioplatform-worker/.../WorkerController.java
  - 新增 POST /worker/shell/execute 端点

bioplatform-worker/.../TaskExecutionService.java
  - 新增 executeShellSync() 同步执行方法

bioplatform-springboot/.../agent/agents/QAAgent.java
  - 改造为工具调用循环模式

bioplatform-springboot/.../agent/agents/PipelineAgent.java
  - getTools() 增加 shell_execute

bioplatform-springboot/.../agent/agents/DataAnalysisAgent.java
  - getTools() 增加 shell_execute

docker-compose-remote.yml
  - backend 增加 volumes: /:/host:ro
```

### 不需要修改

- AgentToolExecutor — 自动发现 @Component Tool
- AgentOrchestrator — 意图路由不变
- LLMClient — 工具调用协议不变
- Dockerfile.local — 不需要在容器内装工具

## 七、部署步骤

```bash
# 1. 本地构建 JAR
cd bioplatform-springboot && mvn package -DskipTests -B

# 2. 构建 Docker 镜像
cd .. && docker build -f bioplatform-springboot/Dockerfile.local -t bioplatform-backend .

# 3. 传输到远程并部署
docker save bioplatform-backend | ssh aliyun 'docker load'
ssh aliyun 'cd /path/to/bioplatform && docker-compose -f docker-compose-remote.yml up -d backend'
```

## 八、验证方式

```
用户: "服务器磁盘空间还剩多少？"
Agent: shell_execute("df -h") → 通过 chroot /host 在宿主机执行 → 返回磁盘信息

用户: "帮我查下数据库里有多少项目"
Agent: shell_execute("mysql -u root bioplatform -e 'SELECT COUNT(*) FROM projects'") → 宿主机 mysql 执行

用户: "看看 /data 目录结构"
Agent: shell_execute("find /data -maxdepth 2 -type d") → 宿主机 find 执行

用户: "这个 BAM 文件的比对统计"
Agent: shell_execute("samtools flagstat /data/sample1.bam") → 宿主机 samtools 执行

用户: "在计算节点上跑个 fastqc"
Agent: shell_execute("fastqc /data/sample1.fq", worker_id="worker-xxx") → 转发到 Worker 执行

用户: "什么是 FASTA 格式？"    → 走 format_info 专用工具，不走 shell
```
