# 🧬 BioPlatform - 生物信息学云平台

> 一站式生物信息学数据分析云平台，集成项目管理、流程编排、数据管理和 AI 智能助手。

---

## 📋 目录

- [项目简介](#项目简介)
- [技术亮点](#技术亮点)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [功能模块](#功能模块)
- [快速开始](#快速开始)
- [开发指南](#开发指南)
- [API 接口说明](#api-接口说明)
- [AI Agent 架构](#ai-agent-架构)
- [部署说明](#部署说明)

---

## 项目简介

**BioPlatform** 是一个面向生物信息学研究的云端协作平台，提供从数据上传、流程管理到智能分析的全流程支持。平台采用前后端分离架构，后端基于 Spring Boot 构建 RESTful API，前端使用 Vue 3 提供现代化的管理后台和用户门户。

核心特性：

- **RBAC 权限管理** — 基于角色的细粒度访问控制（菜单/按钮/接口三级权限）
- **项目管理** — 按物种、基因组版本组织研究项目
- **Pipeline 编排** — 支持生物信息学分析流程的定义、配置与执行监控
- **数据管理** — 上传和管理 FASTA/VCF/BAM/FASTQ 等生物数据文件
- **AI 智能助手** — SSE 流式输出、多轮对话、意图识别、工具调用的多智能体架构
- **操作日志审计** — AOP 切面自动记录关键操作

---

## 技术亮点

### 🚀 SSE 流式对话

AI 助手采用 **Server-Sent Events (SSE)** 实现流式输出，用户可实时看到 AI 逐字生成回复，体验接近 ChatGPT：

- 后端通过 `SseEmitter` + OkHttp 流式读取 LLM API 的 `stream` 响应，逐 token 推送到前端
- 前端使用 `fetch` + `ReadableStream` 解析 SSE 事件，通过 Vue 3 响应式 `ref` 实现实时 DOM 更新
- `SecurityContext` 跨线程传播，解决 Spring Security 在 Tomcat async dispatch 下的鉴权问题
- `RequestAttributeSecurityContextRepository` 确保 async 线程能正确恢复认证上下文

### 🔐 端到端安全设计

- **JWT 双 Token 机制** — Access Token + Refresh Token，支持主动刷新避免无感过期
- **敏感配置加密** — API Key 等敏感字段使用 AES-GCM 端到端加密（前端加密 → 传输 → 后端解密存储 → 读取时遮蔽返回）
- **Spring Security 无状态认证** — 基于 `JwtAuthenticationFilter` 的过滤器链，配合 `ThreadLocal` 用户上下文

### 📦 分片上传 + 断点续传

大文件支持分片上传，配合前端 `spark-md5` 计算文件 hash，实现断点续传和秒传检测。

### 🎯 前后端分离的多应用架构

- **管理后台 (bioplatform-admin)** — 面向管理员的全功能后台，集成项目/流程/数据/用户/AI 管理
- **用户门户 (bioplatform-front)** — 面向研究者的轻量门户，支持项目浏览和 AI 对话
- 两个前端应用共享同一后端 API，但拥有独立的路由、状态管理和鉴权体系

### 🤖 多智能体编排

AI Agent 模块采用编排器模式，根据用户意图自动路由到专业智能体：

- **意图识别引擎** — 正则模式匹配，零延迟路由
- **工具调用链** — LLM 决策 → 工具执行 → 结果回传 → 继续对话
- **对话标题自动生成** — 首条消息发送后自动截取标题，无需额外 LLM 调用

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | LTS 版本 |
| Spring Boot | 3.4.2 | 核心框架 |
| Spring Security | — | 认证授权 |
| MyBatis | 3.0.4 | ORM 框架 |
| MySQL | 8.x | 关系型数据库 |
| Redis | 7.x | 缓存 / 会话 |
| JWT (jjwt) | 0.12.6 | Token 认证 |
| Knife4j | 4.5.0 | API 文档 (OpenAPI 3) |
| PageHelper | 1.4.7 | 分页插件 |
| Hutool | 5.8.27 | 工具库 |
| OkHttp3 | 4.12 | HTTP 客户端（连接池复用） |
| Lombok | — | 代码简化 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 渐进式框架 |
| TypeScript | 5.6~5.7 | 类型系统 |
| Vite | 5.4 | 构建工具 |
| Element Plus | 2.9 | UI 组件库 |
| Pinia | 2.2~2.3 | 状态管理 |
| Vue Router | 4.5 | 路由管理 |
| Axios | 1.7 | HTTP 客户端 |
| ECharts | 5.5 | 图表可视化 |
| GSAP | 3.12 | 动画库 |
| Marked + highlight.js | — | Markdown 渲染 |

**管理后台 (bioplatform-admin)** 额外使用：WangEditor（富文本）、md-editor-v3（Markdown 编辑器）、vue-i18n（国际化）。

### AI 智能体

| 组件 | 说明 |
|------|------|
| AgentOrchestrator | 智能体编排器，负责意图识别与路由 |
| DataAnalysisAgent | 数据分析智能体（文件格式、比对、变异等） |
| PipelineAgent | 流程管理智能体（流水线配置与执行） |
| QAAgent | 通用问答智能体（默认兜底） |
| LLMClient | 大模型调用客户端（支持 OpenAI / 智谱 / 通义千问等） |
| AgentToolExecutor | 工具执行器（BLAST、FastQC、SAMtools 等） |

### 部署

| 技术 | 说明 |
|------|------|
| Docker / Docker Compose | 容器化部署 |
| Nginx | 反向代理 / 静态资源 |

---

## 项目结构

```
bioplatform/
├── bioplatform-springboot/           # 后端 Spring Boot 项目
│   ├── pom.xml
│   └── src/main/java/com/bioplatform/
│       ├── BioplatformApplication.java       # 启动类
│       ├── agent/                            # AI 智能体模块
│       │   ├── AgentOrchestrator.java        # 智能体编排器
│       │   ├── BioAgent.java                 # 智能体接口
│       │   ├── LLMClient.java                # 大模型客户端
│       │   ├── ChatMessage.java              # 对话消息模型
│       │   ├── ToolDefinition.java           # 工具定义
│       │   ├── ToolCall.java                 # 工具调用
│       │   ├── LLMResponse.java             # 模型响应
│       │   ├── agents/                       # 具体智能体实现
│       │   │   ├── DataAnalysisAgent.java
│       │   │   ├── PipelineAgent.java
│       │   │   └── QAAgent.java
│       │   └── tools/                        # 工具定义与执行
│       │       ├── Tool.java
│       │       ├── AgentToolExecutor.java
│       │       └── impl/
│       │           ├── FileInfoTool.java
│       │           ├── FormatInfoTool.java
│       │           └── PipelineSearchTool.java
│       ├── config/                           # 配置类
│       │   ├── SecurityConfig.java           # Spring Security 配置
│       │   └── OperLogAspect.java            # 操作日志切面
│       ├── common/                           # 公共组件
│       │   ├── annotation/
│       │   │   └── OperLog.java              # 操作日志注解
│       │   └── util/
│       │       ├── LoginUserHolder.java       # 登录用户上下文
│       │       ├── JwtTokenProviderUtil.java  # JWT 工具
│       │       └── AesEncryptUtil.java        # AES-GCM 加密工具
│       ├── controller/
│       │   ├── admin/                        # 后台管理接口
│       │   │   ├── AdminAuthController.java
│       │   │   ├── AdminUserController.java
│       │   │   ├── AdminRoleController.java
│       │   │   ├── AdminProjectController.java
│       │   │   ├── AdminPipelineController.java
│       │   │   ├── AdminExecutionController.java
│       │   │   ├── AdminDataFileController.java
│       │   │   ├── AdminAgentController.java
│       │   │   ├── AdminSystemController.java
│       │   │   └── AdminLogController.java
│       │   └── front/                        # 前台公开接口
│       │       ├── FrontAuthController.java
│       │       ├── FrontProjectController.java
│       │       ├── FrontPipelineController.java
│       │       └── FrontAgentController.java
│       ├── dto/                              # 数据传输对象
│       │   ├── admin/
│       │   └── common/
│       │       ├── ApiResponse.java
│       │       └── PageResult.java
│       ├── entity/                           # 实体类
│       │   ├── User.java
│       │   ├── Role.java
│       │   ├── Permission.java
│       │   ├── Project.java
│       │   ├── Pipeline.java
│       │   ├── PipelineExecution.java
│       │   ├── DataFile.java
│       │   ├── AgentConversation.java
│       │   ├── AgentMessage.java
│       │   ├── AgentTool.java
│       │   ├── SystemConfig.java
│       │   └── OperationLog.java
│       ├── filter/                           # 过滤器
│       │   └── JwtAuthenticationFilter.java  # JWT 认证过滤器
│       ├── mapper/                           # MyBatis Mapper
│       │   └── *.java
│       ├── service/                          # 业务层
│       │   ├── UserService.java
│       │   ├── RoleService.java
│       │   ├── ProjectService.java
│       │   ├── PipelineService.java
│       │   ├── PipelineExecutionService.java
│       │   ├── DataFileService.java
│       │   ├── AgentService.java
│       │   ├── SystemService.java
│       │   ├── OperationLogService.java
│       │   └── impl/                         # 实现类
│       └── security/                         # Spring Security 配置
│           └── CustomUserDetailsService.java
│   └── src/main/resources/
│       ├── application.yml                   # 公共配置
│       ├── application-dev.yml               # 开发环境配置
│       ├── application-prod.yml              # 生产环境配置
│       └── mapper/**/*.xml                   # MyBatis XML 映射
│
├── bioplatform-vue3/                        # 前端 Vue 3 项目
│   ├── bioplatform-admin/                   # 管理后台
│   │   ├── package.json
│   │   ├── tsconfig.json
│   │   ├── index.html
│   │   └── src/
│   │       ├── main.ts                      # 入口
│   │       ├── App.vue
│   │       ├── router/index.ts              # 路由配置
│   │       ├── stores/user.ts               # 用户状态 (Pinia)
│   │       ├── utils/
│   │       │   └── http/axios.ts            # Axios 封装（Token 刷新、加密）
│   │       ├── api/                         # API 接口封装
│   │       │   ├── loginApi.ts
│   │       │   ├── userApi.ts
│   │       │   ├── projectApi.ts
│   │       │   ├── pipelineApi.ts
│   │       │   ├── executionApi.ts
│   │       │   ├── dataFileApi.ts
│   │       │   ├── agentApi.ts              # Agent API（含 SSE 流式）
│   │       │   └── systemApi.ts
│   │       ├── layout/AdminLayout.vue
│   │       └── views/
│   │           ├── login/LoginView.vue
│   │           ├── dashboard/DashboardView.vue
│   │           ├── project/ProjectView.vue
│   │           ├── pipeline/PipelineView.vue
│   │           ├── pipeline/ExecutionView.vue
│   │           ├── data/DataView.vue
│   │           ├── agent/AgentView.vue      # AI 助手（SSE 流式渲染）
│   │           ├── system/user/UserView.vue
│   │           ├── system/config/ConfigView.vue
│   │           └── monitor/LogView.vue
│   │
│   └── bioplatform-front/                   # 用户门户
│       ├── package.json
│       └── src/
│           ├── main.ts
│           ├── router/index.ts
│           ├── stores/user.ts
│           ├── api/
│           │   ├── authApi.ts
│           │   ├── projectApi.ts
│           │   ├── pipelineApi.ts
│           │   └── agentApi.ts              # Agent API（含 SSE 流式）
│           ├── layout/MainLayout.vue
│           ├── components/
│           │   ├── ChatMessage.vue          # AI 对话气泡组件
│           │   ├── ProjectCard.vue
│           │   ├── PipelineCard.vue
│           │   └── LoginModal.vue
│           └── views/
│               ├── home/HomeView.vue
│               ├── project/ProjectView.vue
│               ├── pipeline/PipelineView.vue
│               ├── agent/AgentView.vue      # AI 助手（SSE 流式渲染）
│               └── about/AboutView.vue
│
└── database/
    └── bioplatform.sql                     # 数据库建表 & 初始化 SQL
```

---

## 功能模块

### 1. 用户管理 (RBAC)

- 用户注册 / 登录（密码 + 验证码）
- JWT Token 认证（Access Token + Refresh Token，主动刷新机制）
- 基于角色的访问控制：`ROLE_USER` / `ROLE_ADMIN`
- 权限树管理：菜单 → 按钮 → 接口 三级权限
- 默认管理员账号：`admin` / `admin123`

### 2. 项目管理

- 创建/编辑/归档研究项目
- 按物种（organism）和基因组版本（genome_version）分类
- 公开 / 私有项目设置
- 项目关联数据文件和分析流程

### 3. 流程管理 (Pipeline)

- 生物信息学分析流程定义（JSON 配置步骤）
- 支持分类：QC / Alignment / Assembly / Annotation 等
- 关联 Docker 镜像，可配置超时时间
- 流程执行监控（PENDING → RUNNING → SUCCESS/FAILED）
- 输入参数 JSON 存储，输出结果路径追踪

### 4. 数据管理

- 支持上传 FASTA / VCF / BAM / FASTQ / BED / GFF 等生物数据格式
- 分片上传 + 断点续传（前端 spark-md5 文件 hash）
- 存储配额检查
- 按项目、文件类型、物种分类管理
- 文件下载与删除

### 5. AI 智能助手

- **SSE 流式输出** — 实时逐字渲染，体验接近 ChatGPT
- 多轮对话（基于 SSE 流式传输）
- 意图识别自动路由到合适的智能体
- 上下文管理（最近 20 条历史消息）
- 工具调用：BLAST、FastQC、SAMtools、BEDtools 等
- 对话历史持久化存储
- 对话标题自动生成（首条消息截取）
- 对话多选删除 / 一键清空
- 支持多模型切换（GPT-4 / DeepSeek 等）

### 6. 系统管理

- 系统配置键值对管理
- 敏感配置端到端加密（AES-GCM）
- LLM 配置动态加载（DB-only，支持运行时切换模型）
- 操作日志审计（AOP 自动记录）
- 角色与权限配置

---

## 快速开始

### 环境要求

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | Java 运行时 |
| Node.js | 18+ | 前端构建 |
| Maven | 3.8+ | 后端构建 |
| MySQL | 8.0+ | 数据库 |
| Redis | 7.0+ | 缓存 |

或使用 Docker Compose 一键启动所有服务。

### 方式一：Docker Compose 部署（推荐）

```bash
# 克隆项目
git clone git@github.com:ShiganLuo/bioplatform.git
cd bioplatform

# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps
```

启动后访问：

| 服务 | 地址 |
|------|------|
| 管理后台 | http://localhost:3000 |
| 用户门户 | http://localhost:3001 |
| 后端 API | http://localhost:8080 |
| API 文档 (Knife4j) | http://localhost:8080/doc.html |

### 方式二：本地开发

#### 1. 初始化数据库

也可以直接使用项目根目录脚本，仅启动源码调试依赖服务（MySQL/Redis）：

```bash
./docker-deploy.sh debug
```

```bash
# 登录 MySQL
mysql -u root -p

# 创建数据库并导入
mysql> CREATE DATABASE bioplatform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
mysql> SOURCE database/bioplatform.sql;
```

#### 2. 启动后端

```bash
cd bioplatform-springboot

# 编译并启动（默认 dev 配置）
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/bioplatform-springboot-0.0.1-SNAPSHOT.jar
```

后端启动后监听 `http://localhost:8080`。

#### 3. 启动管理后台

```bash
cd bioplatform-vue3/bioplatform-admin

pnpm install
pnpm dev
```

默认监听 `http://localhost:3000`。

#### 4. 启动用户门户

```bash
cd bioplatform-vue3/bioplatform-front

pnpm install
pnpm dev
```

默认监听 `http://localhost:3001`。

### 默认账号

| 角色 | 用户名 | 密码 | 说明 |
|------|--------|------|------|
| 管理员 | `admin` | `admin123` | 拥有全部权限 |

### API 文档地址

启动后端后，通过 Knife4j 访问：

```
http://localhost:8080/doc.html
```

---

## 开发指南

### 后端本地开发

#### 环境变量配置

后端通过 `application-dev.yml` 和 `application-prod.yml` 区分环境。

**开发环境关键配置（application-dev.yml）：**

```yaml
# 数据库
spring.datasource.url: jdbc:mysql://localhost:3308/bioplatform
spring.datasource.username: root
spring.datasource.password: 123456

# Redis
spring.data.redis.host: localhost
spring.data.redis.port: 6380

# JWT
security.jwt.secret: REDACTED_DEV_JWT_SECRET

# 文件上传
app.upload.dir: /home/luosg/uploads/bioplatform

# LLM（可选）
llm.enabled: false
llm.provider: openai
llm.api-key: ***
llm.model: gpt-4
```

**生产环境环境变量（application-prod.yml）：**

```bash
# 通过环境变量注入敏感配置
DB_PASSWORD=your_db_password
REDIS_PASSWORD=your_redis_password
JWT_SECRET=your_jwt_secret_key_at_least_32_chars
LLM_ENABLED=true
LLM_API_KEY=<your-api-key>
LLM_PROVIDER=openai
LLM_MODEL=gpt-4
```

### 数据库初始化

```bash
# 导入建表语句和初始数据
mysql -u root -p bioplatform < database/bioplatform.sql
```

SQL 包含：

- 14 张数据表（users、roles、permissions、projects、pipelines 等）
- 默认角色：`ROLE_USER`、`ROLE_ADMIN`
- 完整权限树（系统管理、项目、流程、数据、AI Agent、日志）
- 默认管理员用户
- 默认系统配置项
- 默认 Agent 工具列表（blast、fastqc、samtools 等）

### 前端本地开发

```bash
# 管理后台
cd bioplatform-vue3/bioplatform-admin
pnpm install
pnpm dev        # Vite 开发服务器
pnpm build      # 生产构建

# 用户门户
cd bioplatform-vue3/bioplatform-front
pnpm install
pnpm dev
pnpm build
```

前端开发服务器已配置代理，将 `/api` 请求转发到后端 `http://localhost:8080`。

---

## API 接口说明

### 后台管理接口 (`/api/admin/*`)

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **认证** | POST | `/api/admin/users/login` | 用户登录 |
| | POST | `/api/admin/users/register` | 用户注册 |
| | POST | `/api/admin/users/captchaLogin` | 验证码登录 |
| | POST | `/api/admin/users/refreshToken` | 刷新 Token |
| | GET | `/api/admin/users/getEmailCode/email` | 获取邮箱验证码 |
| **用户管理** | GET | `/api/admin/users/list` | 分页用户列表 |
| | POST | `/api/admin/users/create` | 创建用户 |
| | PUT | `/api/admin/users/update` | 更新用户 |
| | DELETE | `/api/admin/users/delete/{id}` | 删除用户 |
| **角色管理** | GET | `/api/admin/roles` | 角色列表 |
| | POST | `/api/admin/roles` | 创建角色 |
| | PUT | `/api/admin/roles/{id}` | 更新角色 |
| | DELETE | `/api/admin/roles/{id}` | 删除角色 |
| **项目管理** | GET | `/api/admin/projects` | 项目列表 |
| | POST | `/api/admin/projects` | 创建项目 |
| | PUT | `/api/admin/projects/{id}` | 更新项目 |
| | DELETE | `/api/admin/projects/{id}` | 删除项目 |
| **流程管理** | GET | `/api/admin/pipelines` | 流程列表 |
| | POST | `/api/admin/pipelines` | 创建流程 |
| | PUT | `/api/admin/pipelines/{id}` | 更新流程 |
| | DELETE | `/api/admin/pipelines/{id}` | 删除流程 |
| **流程执行** | GET | `/api/admin/executions` | 执行记录列表 |
| | POST | `/api/admin/executions` | 启动执行 |
| | PUT | `/api/admin/executions/{id}` | 更新执行状态 |
| **数据管理** | GET | `/api/admin/data-files` | 文件列表 |
| | POST | `/api/admin/data-files/upload` | 上传文件 |
| | GET | `/api/admin/data-files/download/{id}` | 下载文件 |
| | DELETE | `/api/admin/data-files/{id}` | 删除文件 |
| **AI Agent** | GET | `/api/admin/agent/conversations` | 对话列表 |
| | POST | `/api/admin/agent/conversations` | 创建对话 |
| | POST | `/api/admin/agent/chat` | 发送消息（同步） |
| | POST | `/api/admin/agent/chat/stream` | 发送消息（SSE 流式） |
| | DELETE | `/api/admin/agent/conversations/{id}` | 删除对话 |
| | POST | `/api/admin/agent/conversations/batch-delete` | 批量删除对话 |
| | DELETE | `/api/admin/agent/conversations/all` | 清空所有对话 |
| | GET | `/api/admin/agent/conversations/{id}/messages` | 对话消息列表 |
| | GET | `/api/admin/agent/tools` | 可用工具列表 |
| **系统管理** | GET | `/api/admin/system/configs` | 系统配置 |
| | PUT | `/api/admin/system/configs` | 更新配置 |
| **日志** | GET | `/api/admin/logs` | 操作日志列表 |

### 前台公开接口 (`/api/front/*`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/front/projects` | 公开项目列表 |
| GET | `/api/front/projects/{id}` | 项目详情 |
| GET | `/api/front/pipelines` | 公开流程列表 |
| POST | `/api/front/agent/chat` | AI 对话（同步） |
| POST | `/api/front/agent/chat/stream` | AI 对话（SSE 流式） |
| GET | `/api/front/agent/conversations` | 对话列表 |
| GET | `/api/front/agent/conversations/{id}/messages` | 对话消息列表 |
| DELETE | `/api/front/agent/conversations/{id}` | 删除对话 |
| POST | `/api/front/agent/conversations/batch-delete` | 批量删除对话 |
| DELETE | `/api/front/agent/conversations/all` | 清空所有对话 |

> 所有后台接口需携带 `Authorization: Bearer <token>` 请求头。
> 前台接口 `/api/front/**` 无需认证即可访问（AI 对话需登录）。

---

## AI Agent 架构

### 多智能体工作流程

```
用户消息
    │
    ▼
┌─────────────────────────┐
│   AgentOrchestrator     │  ← 智能体编排器
│  ┌───────────────────┐  │
│  │   意图识别引擎     │  │  ← 正则模式匹配
│  │  (Regex Patterns)  │  │
│  └─────────┬─────────┘  │
│            │             │
│    ┌───────┼───────┐    │
│    ▼       ▼       ▼    │
│ ┌──────┐┌──────┐┌──────┐│
│ │Data  ││Pipe- ││  QA  ││  ← 三个专业智能体
│ │Analy-││line  ││Agent ││
│ │sis   ││Agent ││      ││
│ └──────┘└──────┘└──────┘│
│            │             │
│  ┌─────────▼─────────┐  │
│  │  LLMClient        │  │  ← 大模型调用（SSE 流式）
│  │  (OpenAI API)     │  │
│  └─────────┬─────────┘  │
│            │             │
│  ┌─────────▼─────────┐  │
│  │  AgentToolExecutor │  │  ← 工具调用执行
│  │  (BLAST, FastQC..) │  │
│  └───────────────────┘  │
└─────────────────────────┘
    │
    ▼
  流式回复 → SSE 推送到前端 → 持久化到 agent_messages
```

### SSE 流式对话流程

```
前端 fetch POST /api/admin/agent/chat/stream
    │
    ▼
后端 SseEmitter（5 分钟超时）
    │
    ├─ 1. 保存用户消息到 DB
    ├─ 2. 获取历史上下文（最近 20 条）
    ├─ 3. OkHttp 流式调用 LLM API（stream: true）
    │     │
    │     ├─ 逐 token 解析 SSE data: 行
    │     ├─ 过滤 null delta（LLM 返回的空 token）
    │     └─ emitter.send({"delta":"token"})
    │
    ├─ 4. 流结束：保存完整助手回复到 DB
    ├─ 5. 自动生成对话标题（截取用户首条消息）
    └─ 6. emitter.send({"done":true,"conversationId":N})

前端 ReadableStream 逐 chunk 解析
    │
    ├─ 解析 data: 行（兼容有无空格）
    ├─ streamingContent.value += token（Vue 响应式）
    ├─ DOM 实时更新
    └─ 收到 done → 将流式内容转入 messages 数组
```

### 意图识别规则

| 意图 | 匹配关键词 | 路由到 |
|------|-----------|--------|
| 流程管理 | `流水线` `pipeline` `流程` `workflow` `运行` `执行` | PipelineAgent |
| 数据分析 | `VCF` `BAM` `FASTA` `FASTQ` `BED` `比对` `变异` `格式` `序列` | DataAnalysisAgent |
| 质量控制 | `QC` `质控` `fastqc` `过滤` `trim` | DataAnalysisAgent |
| 通用问答 | （默认兜底） | QAAgent |

### Agent 工具列表

| 工具 | 分类 | 说明 |
|------|------|------|
| `blast` | bioinformatics | BLAST 序列比对 |
| `fastqc` | bioinformatics | FASTQ 质量控制 |
| `samtools` | bioinformatics | SAM/BAM 文件操作 |
| `bedtools` | bioinformatics | 基因组区间操作 |
| `web_search` | data | 联网搜索 |
| `file_read` | data | 读取和解析数据文件 |

---

## 部署说明

### Docker Compose 部署

#### 服务组成

```yaml
services:
  mysql:        # MySQL 8 数据库
  redis:        # Redis 7 缓存
  backend:      # Spring Boot 后端
  nginx:        # Nginx 反向代理 + 静态资源
```

#### 部署步骤

```bash
# 1. 克隆代码
git clone git@github.com:ShiganLuo/bioplatform.git
cd bioplatform

# 2. 配置环境变量（生产环境）
export DB_PASSWORD=your_secure_password
export REDIS_PASSWORD=your_redis_password
export JWT_SECRET=your_jwt_secret_at_least_32_chars
export LLM_API_KEY=<your-api-key>

# 3. 构建并启动
docker compose up -d --build

# 4. 导入建表 SQL（首次启动）
docker compose exec mysql mysql -u root -p$DB_PASSWORD bioplatform < database/bioplatform.sql

# 5. 查看日志
docker compose logs -f backend
```

#### 生产环境配置要点

```yaml
# application-prod.yml 关键配置
server:
  port: 8080
  tomcat:
    max-threads: 200
    max-connections: 8192

spring:
  datasource:
    url: jdbc:mysql://mysql:3306/bioplatform?useSSL=true
    username: bioplatform          # 不使用 root
    password: ${DB_PASSWORD}       # 环境变量注入
  data:
    redis:
      host: redis
      password: ${REDIS_PASSWORD}

llm:
  enabled: ${LLM_ENABLED:false}
  api-key: ***
  model: ${LLM_MODEL:gpt-4}

knife4j:
  enable: false                    # 生产环境关闭 API 文档
```

### Nginx 反向代理配置参考

```nginx
server {
    listen 80;
    server_name bioplatform.your-domain.com;

    # 管理后台
    location /admin {
        alias /usr/share/nginx/html/admin;
        try_files $uri $uri/ /admin/index.html;
    }

    # 用户门户
    location / {
        root /usr/share/nginx/html/front;
        try_files $uri $uri/ /index.html;
    }

    # API 代理
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # SSE 流式支持
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }

    # WebSocket 代理
    location /ws/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 常见问题

**Q: 启动后端报数据库连接错误？**
A: 确认 MySQL 已启动并创建了 `bioplatform` 数据库，且 `application-dev.yml` 中的连接信息正确。

**Q: LLM 功能如何启用？**
A: 在后台系统配置页面设置 `llm_api_key`、`llm_base_url`、`llm_model`。支持 OpenAI 兼容 API（GPT-4、DeepSeek、智谱、通义千问等）。

**Q: AI 对话没有回复？**
A: 检查后端日志，常见原因：1) LLM API Key 未配置或为遮蔽值；2) Base URL 未配置；3) 网络不通。配置项均在后台「系统配置」页面管理。

**Q: 如何自定义 Agent 工具？**
A: 在 `agent/tools/impl/` 目录下实现 `Tool` 接口，并在 `agent_tools` 表中注册。

**Q: SSE 流式对话在 Nginx 后面不工作？**
A: 确保 Nginx 配置了 `proxy_buffering off` 和 `proxy_cache off`，否则 Nginx 会缓冲 SSE 响应导致无法流式推送。

---

## 开发者

- **luosg** — 项目作者

---

## License

本项目为私有项目，未经授权禁止复制、分发或用于商业用途。
