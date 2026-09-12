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
├── Spring Boot API — 业务逻辑、用户管理、项目管理（Docker 容器内运行）
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
├── Worker 进程（Spring Boot，端口 8081）
│   ├── 注册到公网服务器（上报算力、工具）
│   ├── 接收计算任务
│   ├── 调用本地工具（BLAST/FastQC/SAMtools...）
│   └── 回传结果
├── 生信工具链（conda/docker）
└── 数据存储目录
```

## 三、部署步骤（完整流程）

### 3.1 SSH 密钥认证（内网服务器 → 公网服务器）

内网服务器需要免密 SSH 到公网服务器，用于建立隧道。

```bash
# 在内网服务器上执行

# 1. 生成密钥（如果还没有）
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519_bioplatform -N ""

# 2. 复制公钥到公网服务器
ssh-copy-id -i ~/.ssh/id_ed25519_bioplatform.pub luosg@39.97.180.240 -p 20225

# 3. 测试免密登录
ssh -i ~/.ssh/id_ed25519_bioplatform luosg@39.97.180.240 -p 20225 echo ok
# 应该输出: ok
```

### 3.2 安装 autossh（内网服务器）

autossh 在 SSH 断开后自动重连，比裸 ssh 稳定得多。

```bash
# Debian/Ubuntu
sudo apt install autossh

# CentOS/RHEL
sudo yum install autossh

# 验证安装
which autossh
```

### 3.3 建立 SSH 反向隧道（内网服务器）

在**内网服务器**上执行，把内网的 Worker 端口(8081)映射到公网服务器的 127.0.0.1:18081：

```bash
# 先手动测试（前台运行，Ctrl+C 退出）
ssh -N -R 127.0.0.1:18081:localhost:8081 luosg@39.97.180.240 -p 20225

# 测试通过后，用 autossh 后台运行
autossh -M 0 -N \
  -o "ServerAliveInterval 30" \
  -o "ServerAliveCountMax 3" \
  -o "ExitOnForwardFailure yes" \
  -R 127.0.0.1:18081:localhost:8081 \
  luosg@39.97.180.240 -p 20225
```

参数说明：
- `-N` — 不执行远程命令，只做端口转发
- `-R 127.0.0.1:18081:localhost:8081` — 把公网服务器的 127.0.0.1:18081 转发到内网的 localhost:8081
- `-M 0` — autossh 用 SSH 自带心跳检测，不额外开监控端口
- `ServerAliveInterval 30` — 每 30 秒发一次心跳
- `ServerAliveCountMax 3` — 连续 3 次心跳无响应则断开重连
- `ExitOnForwardFailure yes` — 端口转发失败时退出（避免僵尸进程）

**在公网服务器上验证隧道是否通了**：

```bash
# 公网服务器上执行
curl http://127.0.0.1:18081/worker/health
# 应该返回: {"status":"UP","cpuCores":192,...}

ss -tlnp | grep 18081
# 应该看到: LISTEN 0 128 127.0.0.1:18081 0.0.0.0:*
```

### 3.4 SSH 隧道开机自启（内网服务器）

用 systemd 用户服务管理 autossh，开机自动启动，崩溃自动重启。

```bash
# 在内网服务器上执行

# 1. 创建服务文件
mkdir -p ~/.config/systemd/user
cat > ~/.config/systemd/user/ssh-tunnel.service << 'EOF'
[Unit]
Description=SSH Reverse Tunnel to Public Server
After=network-online.target

[Service]
Type=simple
ExecStart=/usr/bin/autossh -M 0 -N \
  -o "ServerAliveInterval 30" \
  -o "ServerAliveCountMax 3" \
  -o "ExitOnForwardFailure yes" \
  -o "StrictHostKeyChecking=accept-new" \
  -R 127.0.0.1:18081:localhost:8081 \
  luosg@39.97.180.240 -p 20225
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
EOF

# 2. 启用 linger（关键！没有这行，SSH 断开后服务会被杀掉）
loginctl enable-linger

# 3. 启动服务
systemctl --user daemon-reload
systemctl --user enable --now ssh-tunnel

# 4. 查看状态
systemctl --user status ssh-tunnel

# 5. 查看日志（如果出问题）
journalctl --user -u ssh-tunnel -f
```

> **`loginctl enable-linger` 是什么？**
> 默认情况下，用户注销后，该用户的所有 systemd 用户服务都会被杀掉。
> `enable-linger` 告诉系统：即使这个用户没有登录，也要保留他的服务。
> 没有这行，你 SSH 断开后隧道就断了。

**常用管理命令**：

```bash
# 查看状态
systemctl --user status ssh-tunnel

# 停止隧道
systemctl --user stop ssh-tunnel

# 重启隧道
systemctl --user restart ssh-tunnel

# 查看日志
journalctl --user -u ssh-tunnel -f

# 禁用开机自启
systemctl --user disable ssh-tunnel
```

### 3.5 socat 端口转发（公网服务器）

**为什么需要这一步？**

SSH 隧道绑在 `127.0.0.1:18081`，但后端跑在 Docker 容器里。容器的 `localhost` 是容器自己，不是宿主机，所以容器连不上 `127.0.0.1:18081`。

```
宿主机
├── SSH 隧道监听 127.0.0.1:18081  ← 只有宿主机进程能访问
└── Docker 容器
    └── 访问 localhost:18081  ← 指向容器自己，连不到！
```

socat 在宿主机上做一个端口转发：把 Docker 网桥 IP 的 18081 转发到 127.0.0.1:18081。

**在公网服务器上执行**：

```bash
# 1. 安装 socat（如果没有）
sudo apt install socat   # Debian/Ubuntu
sudo yum install socat   # CentOS/RHEL

# 2. 查看 host.docker.internal 在容器内解析到哪个 IP
docker exec bioplatform-backend cat /etc/hosts | grep host.docker
# 输出: 192.168.100.1  host.docker.internal
#
# ⚠️ 这个 IP 不一定是 blog_net 的网桥 IP（172.19.0.1）！
# host.docker.internal 通过 docker-compose 的 extra_hosts 映射，
# 解析到的是 docker0 网桥 IP，不是自定义网络的网桥 IP。

# 3. 启动 socat（绑定到 host.docker.internal 解析到的 IP）
nohup socat TCP-LISTEN:18081,bind=192.168.100.1,fork,reuseaddr TCP:127.0.0.1:18081 &

# 4. 验证：从容器内测试
docker exec bioplatform-backend wget -q -O- --timeout=3 http://host.docker.internal:18081/worker/health
# 应该返回: {"status":"UP","cpuCores":192,...}
```

**socat 参数说明**：
- `TCP-LISTEN:18081` — 监听 18081 端口
- `bind=192.168.100.1` — 只在这个 IP 上监听（Docker 网桥网关）
- `fork` — 每个连接 fork 子进程处理（支持并发）
- `reuseaddr` — 端口重用（避免重启时 Address already in use）
- `TCP:127.0.0.1:18081` — 转发目标（SSH 隧道入口）

**设置开机自启**（公网服务器上）：

```bash
sudo tee /etc/systemd/system/socat-tunnel.service << 'EOF'
[Unit]
Description=Socat port forward for Docker to SSH tunnel
After=network.target docker.service

[Service]
Type=simple
# bind 到 host.docker.internal 解析的 IP
# 查看方法: docker exec bioplatform-backend cat /etc/hosts | grep host.docker
ExecStart=/usr/bin/socat TCP-LISTEN:18081,bind=192.168.100.1,fork,reuseaddr TCP:127.0.0.1:18081
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now socat-tunnel

# 查看状态
sudo systemctl status socat-tunnel
```

**数据流**：

```
Docker 容器 → host.docker.internal:18081 (解析到 192.168.100.1)
    ↓ socat 转发
宿主机 127.0.0.1:18081 (SSH 隧道入口)
    ↓ SSH 隧道转发
内网服务器 localhost:8081 (Worker 进程)
```

### 3.6 docker-compose 配置（公网服务器）

`docker-compose-remote.yml` 中 backend 服务需要 `extra_hosts` 让容器能解析 `host.docker.internal`：

```yaml
services:
  backend:
    image: bioplatform-backend
    container_name: bioplatform-backend
    extra_hosts:
      - "host.docker.internal:host-gateway"   # 关键：让容器能用 host.docker.internal 访问宿主机
    # ... 其他配置
```

> **`host-gateway` 是什么？**
> Docker 自动把宿主机的网关 IP 映射到容器的 `host.docker.internal`。
> 具体映射到哪个 IP 取决于 Docker 版本和网络配置，
> 用 `docker exec bioplatform-backend cat /etc/hosts | grep host.docker` 查看实际值。

### 3.7 注册计算节点（管理后台）

1. 登录管理后台 → 计算节点管理 → 添加节点
2. URL 填：`http://host.docker.internal:18081`
3. 点击"测试并添加"

**⚠️ 不要填** `http://localhost:18081`（容器的 localhost 是自己，不是宿主机）

如果填错了，点「编辑」修改 URL，不需要删掉重建。

### 3.8 Worker 部署（内网服务器）

```bash
# 1. 安装 JDK 17
sudo apt install openjdk-17-jre-headless
# 或下载解压到用户目录
mkdir -p ~/tools && cd ~/tools
wget https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz
tar xzf jdk-17_linux-x64_bin.tar.gz

# 2. 部署 Worker JAR
mkdir -p ~/worker
cp bioplatform-worker.jar ~/worker/
cd ~/worker

# 3. 启动 Worker（前台测试）
java -jar bioplatform-worker.jar

# 4. 验证 Worker 正常运行
curl http://localhost:8081/worker/health

# 5. 后台运行
nohup java -jar bioplatform-worker.jar > worker.log 2>&1 &

# 6. 设置开机自启（可选）
cat > ~/.config/systemd/user/worker.service << 'EOF'
[Unit]
Description=Bioplatform Worker
After=network-online.target

[Service]
Type=simple
WorkingDirectory=/home/luosg/worker
ExecStart=/usr/bin/java -jar bioplatform-worker.jar
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable --now worker
```

## 四、架构总览

```
用户浏览器
    │
    ▼
┌──────────────────────────────────────────────────┐
│  公网服务器 (2核4G)                               │
│  ┌────────┐  ┌──────────────┐  ┌──────────┐     │
│  │ Nginx  │→ │ Docker       │→ │ MySQL    │     │
│  │ :80    │  │ backend:8080 │  │ Redis    │     │
│  └────────┘  └──────┬───────┘  └──────────┘     │
│                     │                            │
│              ┌──────┴───────┐                    │
│              │ socat 转发    │                    │
│              │ 192.168.100.1│  ← docker0 网桥    │
│              │ :18081       │                    │
│              └──────┬───────┘                    │
│                     │                            │
│              127.0.0.1:18081  ← SSH 隧道入口     │
└─────────────────────┼────────────────────────────┘
                      │ SSH 隧道 (autossh)
                      │
┌─────────────────────┴────────────┐
│ 内网服务器 A                       │
│ Worker localhost:8081             │
│ BLAST/FastQC, 16核64G            │
└──────────────────────────────────┘
```

## 五、多台内网服务器

每台内网服务器映射到公网服务器不同端口：

| 内网服务器 | 隧道端口 | 节点 URL | socat 规则 |
|-----------|---------|---------|-----------|
| A | 18081 | `http://host.docker.internal:18081` | 1 条 |
| B | 18082 | `http://host.docker.internal:18082` | 1 条 |
| C | 18083 | `http://host.docker.internal:18083` | 1 条 |

每台内网服务器需要：
1. 自己的 autossh 隧道（端口不同）
2. 公网服务器上对应的 socat 转发（端口不同）

公网服务器上多条 socat 规则：

```bash
# 方式 1：多个后台进程
nohup socat TCP-LISTEN:18081,bind=192.168.100.1,fork,reuseaddr TCP:127.0.0.1:18081 &
nohup socat TCP-LISTEN:18082,bind=192.168.100.1,fork,reuseaddr TCP:127.0.0.1:18082 &
nohup socat TCP-LISTEN:18083,bind=192.168.100.1,fork,reuseaddr TCP:127.0.0.1:18083 &

# 方式 2：写到 systemd service（推荐）
# 每条 socat 一个 service 文件，或者用 ExecStartPre 启动多条
```

## 六、Worker 设计

Worker 是一个轻量服务，暴露 REST API 供公网服务器调用：

```java
@RestController
@RequestMapping("/worker")
public class WorkerController {

    // 健康检查（公网服务器每 30 秒调用一次）
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

后端通过 `WorkerRegistry` 自动管理节点，每 30 秒健康检查：

- 健康检查失败 → 节点标记为离线，日志输出 `计算节点健康检查失败`
- 任务下发 → 只选择健康且已启用的节点
- 节点选择 → 优先选可用内存最大的节点

## 七、文件传输

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

## 八、安全加固

```bash
# SSH 密钥认证，禁用密码
PasswordAuthentication no
PubkeyAuthentication yes

# 防火墙只开放必要端口
ufw allow 80/tcp    # Nginx
ufw allow 443/tcp   # HTTPS
ufw allow 22/tcp    # SSH
```

## 九、故障排查

### 排查顺序（从内到外）

隧道不通时，按这个顺序排查：

```bash
# === 第 1 步：内网服务器上检查 Worker 是否正常 ===
curl http://localhost:8081/worker/health
# 如果不通 → Worker 没启动或端口不对

# === 第 2 步：内网服务器上检查 autossh 是否在运行 ===
ps aux | grep autossh
# 如果没有 → 启动 autossh 或检查 systemd 服务
systemctl --user status ssh-tunnel

# === 第 3 步：公网服务器上检查隧道端口是否在监听 ===
ss -tlnp | grep 18081
# 应该看到: LISTEN 0 128 127.0.0.1:18081
# 如果没有 → autossh 没连上，检查 SSH 密钥和网络

# === 第 4 步：公网服务器上从宿主机测试隧道 ===
curl http://127.0.0.1:18081/worker/health
# 如果不通 → 隧道没建好，回到第 2 步

# === 第 5 步：公网服务器上检查 socat 是否在运行 ===
ps aux | grep socat
# 如果没有 → 启动 socat
ss -tlnp | grep socat
# 应该看到: LISTEN 0 5 192.168.100.1:18081

# === 第 6 步：公网服务器上从容器内测试 ===
docker exec bioplatform-backend wget -q -O- --timeout=3 http://host.docker.internal:18081/worker/health
# 如果不通 → socat 绑错 IP 或 host.docker.internal 解析不对
docker exec bioplatform-backend cat /etc/hosts | grep host.docker

# === 第 7 步：查看后端日志 ===
docker logs bioplatform-backend 2>&1 | grep -E "健康检查|测试连接" | tail -5
# 看具体错误信息

# === 第 8 步：管理后台测试连接 ===
# 点击"测试连接"按钮，观察后端日志
docker logs -f bioplatform-backend 2>&1 | grep "测试连接"
```

### 常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| 宿主机 curl 127.0.0.1:18081 通，容器不通 | 容器 localhost ≠ 宿主机 | 用 socat + host.docker.internal |
| 容器 curl host.docker.internal 不通 | socat 绑错 IP | `docker exec ... cat /etc/hosts` 确认 IP，socat 绑到那个 IP |
| socat 绑定失败 Address already in use | 端口被占用 | `pkill socat` 杀掉旧进程再启动 |
| autossh 频繁断开 | 网络不稳定 | 加 `-o "ServerAliveInterval 15"` 缩短心跳 |
| systemd 用户服务 SSH 断开后停止 | 没开 linger | `loginctl enable-linger` |
| ss -tlnp 显示 127.0.0.1:18081 | 隧道默认绑 loopback | 正常，配合 socat 使用 |
| 管理后台测试连接 error=null | 连接超时 | 检查 socat 是否在运行，IP 是否正确 |

### 快速诊断脚本

在公网服务器上保存为 `diagnose-tunnel.sh`：

```bash
#!/bin/bash
echo "=== 1. SSH 隧道端口 ==="
ss -tlnp | grep 18081 || echo "❌ 端口 18081 未监听"

echo ""
echo "=== 2. socat 进程 ==="
ps aux | grep socat | grep -v grep || echo "❌ socat 未运行"

echo ""
echo "=== 3. 宿主机测试隧道 ==="
curl -s --max-time 3 http://127.0.0.1:18081/worker/health || echo "❌ 隧道不通"

echo ""
echo "=== 4. host.docker.internal 解析 ==="
docker exec bioplatform-backend cat /etc/hosts | grep host.docker

echo ""
echo "=== 5. 容器内测试 ==="
docker exec bioplatform-backend wget -q -O- --timeout=3 http://host.docker.internal:18081/worker/health 2>&1 || echo "❌ 容器内不通"

echo ""
echo "=== 6. 后端日志（最近 5 条健康检查） ==="
docker logs bioplatform-backend 2>&1 | grep -E "健康检查|测试连接" | tail -5
```

## 十、扩展性

| 内网服务器数量 | 隧道端口 | 节点 URL | socat 规则 |
|--------------|---------|---------|-----------|
| 1 台 | 18081 | `http://host.docker.internal:18081` | 1 条 |
| 2-5 台 | 18081~18085 | `http://host.docker.internal:1808x` | 每台 1 条 |
| 5+ 台 | 服务注册中心 | 自动注册 | 不需要 socat |

后续如果内网服务器增多，可以引入服务注册机制（Consul/自研），Worker 启动时自动向公网服务器注册，无需手动配置端口映射。

> **注意**：多台内网服务器时，每条 socat 规则的 bind IP 相同（都是 host.docker.internal 解析到的 IP），但端口不同。
