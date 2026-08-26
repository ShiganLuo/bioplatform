# 多内网服务器 + 单公网服务器部署方案

## 场景分析

```
公网服务器（小配置）          内网服务器 A（生信计算）    内网服务器 B（生信计算）
  ┌─────────────┐            ┌─────────────┐            ┌─────────────┐
  │ 2核4G       │            │ 16核64G     │            │ 32核128G    │
  │ 公网 IP     │◄──隧道────│ 无公网 IP   │            │ 无公网 IP   │
  │ Nginx + API │──隧道────►│ 计算节点    │            │ 计算节点    │
  │ MySQL + Redis│           │ BLAST/FastQC│            │ RNA-seq/WGS│
  └─────────────┘            └─────────────┘            └─────────────┘
```

**核心思路：公网服务器做门户 + API 网关，内网服务器通过反向隧道接入，承担实际计算。**

## 一、公网服务器（门户 + 网关）

配置低，只跑轻量服务：

```
公网服务器 (2核4G)
├── Nginx           — 静态文件 + 反向代理
├── Spring Boot API — 业务逻辑、用户管理、项目管理
├── MySQL           — 元数据存储
├── Redis           — 缓存
└── 前端静态文件     — admin + front
```

**不跑任何生信计算**，只负责：
- 用户认证、权限管理
- 项目/流程/数据的元数据管理
- AI 对话（转发到 LLM API）
- 文件上传/下载
- 向内网计算节点下发任务

## 二、内网服务器（计算节点）

每台内网服务器运行一个轻量 Worker 进程：

```
内网服务器
├── Worker 进程（Spring Boot 或 Python）
│   ├── 注册到公网服务器（上报算力、工具）
│   ├── 接收计算任务
│   ├── 调用本地工具（BLAST/FastQC/SAMtools...）
│   └── 回传结果
├── 生信工具链（conda/docker）
└── 数据存储目录
```

## 三、反向隧道（关键）

内网服务器无法被公网直接访问，但**内网可以主动连公网**。利用 SSH 反向隧道：

### 方案：SSH 反向隧道

```bash
# 在内网服务器上执行，建立到公网服务器的反向隧道
# 将内网服务器的 Worker 端口(8081)映射到公网服务器的 localhost:18081
ssh -N -R 18081:localhost:8081 user@公网IP -p 22

# 保持连接：autossh 自动重连
autossh -M 0 -N -R 18081:localhost:8081 user@公网IP -p 22
```

效果：
```
公网服务器 localhost:18081  ←──隧道──→  内网服务器 localhost:8081 (Worker)
公网服务器 localhost:18082  ←──隧道──→  内网服务器B localhost:8081 (Worker)
```

公网服务器的 API 通过 `http://localhost:18081` 调用内网 Worker。

### 配置 autossh 开机自启

```bash
# 内网服务器：创建 systemd 用户服务（不需要 root）
mkdir -p ~/.config/systemd/user

cat > ~/.config/systemd/user/ssh-tunnel.service << 'EOF'
[Unit]
Description=SSH Reverse Tunnel to Public Server
After=network-online.target

[Service]
Type=simple
ExecStart=/usr/bin/autossh -M 0 -N -o "ServerAliveInterval 30" -o "ServerAliveCountMax 3" -R 18081:localhost:8081 user@公网IP -p 22
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
EOF

systemctl --user enable ssh-tunnel
systemctl --user start ssh-tunnel
```

## 四、架构总览

```
用户浏览器
    │
    ▼
┌──────────────────────────────────────────────┐
│  公网服务器 (2核4G)                           │
│  ┌────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ Nginx  │→ │ Spring Boot  │→ │ MySQL    │ │
│  │ :80    │  │ API :8080    │  │ Redis    │ │
│  └────────┘  └──────┬───────┘  └──────────┘ │
│                     │                        │
│        ┌────────────┼────────────┐           │
│        │            │            │           │
│  localhost:18081  localhost:18082             │
│        ▲            ▲                        │
└────────┼────────────┼────────────────────────┘
         │ SSH 隧道   │ SSH 隧道
         │            │
┌────────┴──────┐  ┌──┴─────────────┐
│ 内网服务器 A   │  │ 内网服务器 B    │
│ Worker :8081  │  │ Worker :8081   │
│ BLAST/FastQC  │  │ RNA-seq/WGS    │
│ 16核64G       │  │ 32核128G       │
└───────────────┘  └────────────────┘
```

## 五、Worker 设计

Worker 是一个轻量服务，暴露 REST API 供公网服务器调用：

```java
@RestController
@RequestMapping("/worker")
public class WorkerController {

    // 健康检查
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "cpuCores", Runtime.getRuntime().availableProcessors(),
            "freeMemoryMB", Runtime.getRuntime().freeMemory() / 1024 / 1024,
            "tools", listInstalledTools()
        );
    }

    // 执行任务
    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody TaskRequest request) {
        // 调用本地生信工具
        String result = runCommand(request.getCommand(), request.getArgs());
        return Map.of("status", "completed", "output", result);
    }

    // 查询任务状态
    @GetMapping("/tasks/{taskId}/status")
    public Map<String, Object> taskStatus(@PathVariable String taskId) {
        return taskManager.getStatus(taskId);
    }
}
```

### 公网服务器调度逻辑

```java
@Service
public class TaskScheduler {

    // 内网 Worker 地址（通过 SSH 隧道映射到本地端口）
    private final List<String> workers = List.of(
        "http://localhost:18081",  // 内网服务器 A
        "http://localhost:18082"   // 内网服务器 B
    );

    public String dispatchTask(PipelineExecution execution) {
        // 选择负载最低的 Worker
        String worker = selectWorker();

        // 调用 Worker 执行
        return restTemplate.postForObject(
            worker + "/worker/execute",
            buildTaskRequest(execution),
            String.class
        );
    }

    private String selectWorker() {
        // 简单轮询或根据 /health 接口的 cpuCores/freeMemory 选择
        return workers.get(new Random().nextInt(workers.size()));
    }
}
```

## 六、文件传输

内网计算节点需要读写文件，两种方案：

### 方案 A：共享存储（推荐）

如果内网服务器之间有 NFS/共享存储：
```
共享存储挂载点: /data/shared
公网服务器: /data/shared → NFS
内网服务器A: /data/shared → NFS
内网服务器B: /data/shared → NFS
```

### 方案 B：通过 API 传输

如果内网完全隔离，通过公网服务器中转：

```bash
# 上传：用户 → 公网服务器 → 内网 Worker
curl -X POST http://localhost:18081/worker/upload -F "file=@sample.fastq"

# 下载：内网 Worker → 公网服务器 → 用户
curl http://localhost:18081/worker/download/sample_result.vcf -o result.vcf
```

公网服务器作为文件中转站，可以加缓存避免重复传输。

## 七、安全加固

```bash
# SSH 隧道只允许特定端口
# 公网服务器 /etc/ssh/sshd_config
GatewayPorts no              # 隧道只绑定 localhost
AllowTcpForwarding yes
PermitOpen localhost:18081 localhost:18082  # 只允许转发到特定端口

# SSH 密钥认证，禁用密码
PasswordAuthentication no
PubkeyAuthentication yes

# 防火墙只开放必要端口
ufw allow 80/tcp    # Nginx
ufw allow 443/tcp   # HTTPS
ufw allow 22/tcp    # SSH
```

## 八、最小化部署步骤

### 公网服务器

```bash
# 1. 安装 JDK 17
sudo apt install openjdk-17-jre-headless mysql-server redis-server nginx

# 2. 导入数据库
mysql -u root -p bioplatform < database/bioplatform.sql

# 3. 部署后端
cp bioplatform.jar /opt/bioplatform/app/
# 配置 application-prod.yml 中的数据库连接

# 4. 部署前端
cp -r dist/* /opt/bioplatform/frontend/

# 5. 配置 Nginx（参考 DEPLOY_WITHOUT_DOCKER.md）

# 6. 启动后端
systemctl start bioplatform
```

### 内网服务器

```bash
# 1. 安装 JDK 17（用户目录）
mkdir -p ~/tools && cd ~/tools
wget <jdk-url> && tar xzf <jdk>

# 2. 部署 Worker
cp worker.jar /opt/worker/

# 3. 建立 SSH 隧道
ssh-keygen -t ed25519
ssh-copy-id user@公网IP
autossh -M 0 -N -R 18081:localhost:8081 user@公网IP

# 4. 启动 Worker
nohup java -jar worker.jar > worker.log 2>&1 &

# 5. 验证
curl http://localhost:8081/worker/health
```

## 九、扩展性

| 内网服务器数量 | 公网服务器映射 | 说明 |
|--------------|--------------|------|
| 1 台 | localhost:18081 | 最简单 |
| 2-5 台 | localhost:18081~18085 | 每台一个端口 |
| 5+ 台 | 服务注册中心 | Worker 启动时自动注册，公网服务器动态发现 |

后续如果内网服务器增多，可以引入服务注册机制（Consul/自研），Worker 启动时自动向公网服务器注册，无需手动配置端口映射。
