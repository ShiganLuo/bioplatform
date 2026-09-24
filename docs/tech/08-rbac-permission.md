# 08 - RBAC 权限控制设计

## 一、设计目标

基于角色的访问控制（Role-Based Access Control），实现三个层次的权限防线：

| 层次 | 位置 | 职责 | 失败后果 |
|---|---|---|---|
| 接口层 | Spring Security + @PreAuthorize | 拒绝未授权的 API 调用 | 返回 403 JSON |
| 路由层 | Vue Router 守卫 | 阻止未授权的页面访问 | 重定向到 /dashboard |
| 视图层 | v-permission 指令 + 菜单过滤 | 隐藏无权操作的 UI 元素 | DOM 移除，用户不可见 |

三层互补：接口层是安全底线（即使前端被绕过也能拦截），路由层和视图层是用户体验（不该看到的不展示）。

## 二、数据模型

### 2.1 ER 关系

```
users ──M:N──> roles ──M:N──> permissions
  │               │                │
  └─ user_roles ──┘  └─ role_permissions ──┘
```

四张核心表：

- `users` — 用户基本信息
- `roles` — 角色定义（ROLE_USER / ROLE_ADMIN）
- `permissions` — 权限节点树（菜单、按钮、API 三种类型）
- `user_roles` / `role_permissions` — 两张关联表，实现 M:N 关系

### 2.2 权限树结构

权限以树形组织，`parent_id` 指向父节点，根节点 `parent_id = 0`：

```
system (菜单)
├── system:user:list (菜单)
│   ├── system:user:create (按钮/API)
│   ├── system:user:edit
│   ├── system:user:delete
│   └── system:user:view
├── system:role:list (菜单)
│   ├── system:role:create
│   ├── system:role:edit
│   └── system:role:delete
├── system:config:list (菜单)
│   └── system:config:edit
└── system:permission:list (菜单)

project (菜单)
├── project:list
│   ├── project:create
│   ├── project:edit
│   └── project:delete

pipeline (菜单)
├── pipeline:list
│   ├── pipeline:create / edit / delete / run

data (菜单)
├── data:list
│   ├── data:upload / download / delete

agent (菜单)
├── agent:chat
└── agent:tool:list

log:operation:list (菜单)
```

### 2.3 权限类型

| type 值 | 含义 | 控制对象 |
|---|---|---|
| 1 (menu) | 菜单权限 | 控制侧边栏菜单项是否可见 |
| 2 (button) | 按钮权限 | 控制页面内操作按钮是否可见 |
| 3 (api) | 接口权限 | 控制后端 API 是否可调用 |

### 2.4 角色-权限映射

**ROLE_ADMIN**：拥有所有权限节点。

**ROLE_USER**：仅拥有业务功能的读写子集：

| 权限标识 | 说明 |
|---|---|
| project:list / create / edit | 项目管理（不含删除） |
| pipeline:list / run | 流程管理（不含创建/编辑/删除） |
| data:list / upload / download | 数据管理（不含删除） |
| agent:chat / agent:tool:list | AI 助手 |

不包含：system:*（用户管理、角色管理、系统配置）、log:operation:list（操作日志）。

## 三、后端架构

### 3.1 认证流程

```
客户端请求 → JwtAuthenticationFilter → SecurityContext
                                            ↓
                                    loadUserByUsername()
                                            ↓
                                    查 roles → 构建 authorities
                                            ↓
                                    UsernamePasswordAuthenticationToken
                                            ↓
                                    @PreAuthorize 检查 authorities
```

`CustomUserDetailsService` 从数据库加载用户角色，转换为 `GrantedAuthority` 列表（带 `ROLE_` 前缀）。Spring Security 将其存入 `SecurityContext`，`@PreAuthorize` 读取并匹配。

### 3.2 接口级权限控制

在 Controller 类级别使用 `@PreAuthorize`：

```java
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController { ... }
```

**角色划分原则**：

| 分类 | Controller | 理由 |
|---|---|---|
| 仅管理员 | SystemController | 操作全局配置，影响所有用户 |
| 仅管理员 | UserController | 用户增删改查是管理职能 |
| 仅管理员 | RoleController | 角色分配决定权限边界 |
| 仅管理员 | WorkflowTemplateController | 模板是系统级资源 |
| 仅管理员 | FeedbackController | 管理所有用户的反馈 |
| 仅管理员 | WorkerController | 计算节点是基础设施 |
| 仅管理员 | LogController | 操作日志涉及审计 |
| 已登录即可 | ProjectController | 业务层已按 userId 过滤数据 |
| 已登录即可 | PipelineController | 同上 |
| 已登录即可 | ExecutionController | 同上 |
| 已登录即可 | SampleMetaController | 同上 |
| 已登录即可 | DataFileController | 同上 |
| 已登录即可 | AgentController | 同上，已有会话隔离 |

### 3.3 拒绝访问处理

403 响应统一由 `AccessDeniedEntryPoint` 处理，返回标准 JSON：

```json
{ "code": 403, "message": "权限不足，需要管理员角色", "data": null }
```

避免 Spring Security 默认的空白页或 HTML 错误页。

### 3.4 用户信息接口

`GET /api/admin/auth/userInfo` 返回完整的用户信息，含角色列表：

```json
{
  "id": 1,
  "username": "admin",
  "nickName": "管理员",
  "avatarUrl": "...",
  "roles": ["ROLE_ADMIN"]
}
```

前端在登录后调用此接口获取 roles，存入 Pinia store，供路由守卫和指令使用。

## 四、前端架构

### 4.1 数据流

```
登录 → getUserInfo() → store.userInfo.roles → 三个消费方:
                                              ├→ 路由守卫 (router.beforeEach)
                                              ├→ 菜单过滤 (AdminLayout v-if)
                                              └→ v-permission 指令 (DOM 移除)
```

`userInfo` 同时持久化到 `localStorage`，页面刷新后由 `initUserInfo()` 恢复。

### 4.2 路由守卫

路由定义中通过 `meta.roles` 声明所需角色：

```ts
{
  path: 'system/users',
  meta: { title: '用户管理', roles: ['ROLE_ADMIN'] }
}
```

守卫逻辑：

```
目标路由有 meta.roles ?
  ├─ 是 → 用户角色与 meta.roles 有交集 ?
  │        ├─ 是 → 放行
  │        └─ 否 → 重定向 /dashboard
  └─ 否 → 放行（已登录即可）
```

需要角色限制的路由：system/users、system/config、system/templates、feedback、workers、monitor/logs。

### 4.3 自定义指令 v-permission

全局注册，用于按钮级别的细粒度控制：

```vue
<!-- 仅管理员可见的操作按钮 -->
<el-button v-permission="['ROLE_ADMIN']">删除</el-button>

<!-- 多角色都可见 -->
<el-button v-permission="['ROLE_ADMIN', 'ROLE_USER']">编辑</el-button>
```

指令实现逻辑：

1. 读取 `userStore.userInfo.roles`
2. 与指令参数取交集
3. 交集为空 → 移除 DOM 元素（`el.parentNode?.removeChild(el)`）
4. 更新时重新检查（`updated` 钩子）

### 4.4 菜单过滤

侧边栏中系统管理和系统监控两个子菜单整体受角色控制：

```vue
<el-sub-menu v-if="userStore.hasRole('ROLE_ADMIN')" index="system">
  <template #title><el-icon><Setting /></el-icon><span>系统管理</span></template>
  <!-- 用户管理、系统配置、流程模板、反馈管理、计算节点 -->
</el-sub-menu>

<el-sub-menu v-if="userStore.hasRole('ROLE_ADMIN')" index="monitor">
  <template #title><el-icon><DataLine /></el-icon><span>系统监控</span></template>
  <!-- 操作日志 -->
</el-sub-menu>
```

普通用户登录后侧边栏只看到：仪表盘、项目管理、流程管理、执行监控、数据管理、AI 助手。

## 五、数据隔离（行级权限）

角色控制的是"能不能访问这个功能模块"（接口级），数据隔离控制的是"能看到哪些数据"（行级）。两者是独立的两层机制，缺一不可。

### 5.1 问题现状

当前写入操作已正确隔离（create/upload 通过 `LoginUserHolder.getCurrentUserId()` 设置 owner），但**读取操作完全没有隔离**：

| 接口 | 现状 | 风险 |
|---|---|---|
| `GET /projects/list` | `selectAdminList(name, organism)` 无 userId 过滤 | 返回所有用户的项目 |
| `GET /projects/{id}` | `getProjectById(id)` 无归属校验 | 任何人可查看别人项目详情 |
| `GET /pipelines/list` | `listPipelines(category, page, size)` 无 userId 过滤 | 返回全部流水线 |
| `GET /datafiles/list` | `listAllFiles()` 无 userId 过滤 | 返回所有用户文件 |
| `GET /datafiles/{id}` | `getFileById(id)` 无归属校验 | 可查看/下载别人文件 |
| `GET /executions/list` | userId 为可选参数，不传返回全部 | 默认暴露所有执行记录 |
| `GET /executions/{id}` | `getExecutionById(id)` 无归属校验 | 可查看别人执行详情 |

### 5.2 设计原则

**普通用户**：只能操作自己的数据。查询列表时自动注入 `owner_id = currentUserId` 条件，查询详情时校验归属。

**管理员**：可以查看和管理所有用户的数据。查询列表时不注入 userId 过滤，但写入操作仍需校验（防止误操作）。

判断逻辑在 Service 层实现：

```java
// Service 层示例
public PageResult listProjects(Long userId, boolean isAdmin, int page, int pageSize) {
    if (isAdmin) {
        // 管理员：查看所有项目
        return projectMapper.selectAll(page, pageSize);
    } else {
        // 普通用户：只看自己的项目
        return projectMapper.selectByOwnerId(userId, page, pageSize);
    }
}
```

### 5.3 归属校验模式

对单条记录的查看/修改/删除操作，统一加入归属校验：

```java
// 校验工具方法
private void checkOwnership(Long ownerId, Long currentUserId, boolean isAdmin, String resourceName) {
    if (!isAdmin && !ownerId.equals(currentUserId)) {
        throw new AccessDeniedException("无权访问该" + resourceName);
    }
}
```

### 5.4 需要改造的接口清单

| Controller | 方法 | 改造方式 |
|---|---|---|
| AdminProjectController | `list()` | 普通用户注入 ownerId 过滤 |
| AdminProjectController | `getById()` | 加归属校验 |
| AdminProjectController | `update()` | 加归属校验 |
| AdminProjectController | `delete()` | 加归属校验 |
| AdminPipelineController | `list()` | 普通用户注入 ownerId 过滤 |
| AdminPipelineController | `getById()` | 加归属校验 |
| AdminPipelineController | `update()` | 加归属校验 |
| AdminPipelineController | `delete()` | 加归属校验 |
| AdminDataFileController | `list()` | 普通用户注入 ownerId 过滤 |
| AdminDataFileController | `getById()` | 加归属校验 |
| AdminDataFileController | `download()` | 加归属校验 |
| AdminDataFileController | `delete()` | 加归属校验 |
| AdminExecutionController | `list()` | 普通用户强制注入当前 userId |
| AdminExecutionController | `getById()` | 加归属校验 |
| AdminExecutionController | `cancel()` | 加归属校验 |
| AdminExecutionController | `getLogs()` | 加归属校验 |
| AdminSampleMetaController | `list()` | 通过 projectId 关联校验项目归属 |
| AdminSampleMetaController | `getById()` | 通过 projectId 关联校验项目归属 |

### 5.5 实现方式

Service 层通过 `SecurityContextHolder` 获取当前用户角色，决定是否注入过滤条件。Controller 层不再直接传 userId 参数（避免客户端伪造），而是由 Service 层自行从 SecurityContext 获取。

```java
// Service 层获取当前用户信息
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
boolean isAdmin = auth.getAuthorities().stream()
    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
Long currentUserId = LoginUserHolder.getCurrentUserId();
```

## 六、角色划分总览

| 功能模块 | ROLE_USER | ROLE_ADMIN |
|---|---|---|
| 仪表盘 | ✅ 查看自己的 | ✅ 查看全局统计 |
| 项目管理 | ✅ 增删改查**自己的**项目 | ✅ 管理**所有**项目 |
| 流程管理 | ✅ 查看、执行**自己的** | ✅ 增删改查**所有** |
| 执行监控 | ✅ 查看**自己的**执行 | ✅ 查看**所有**执行 |
| 数据管理 | ✅ 上传、下载**自己的** | ✅ 全部操作含删除**所有** |
| AI 助手 | ✅ 对话（会话隔离） | ✅ 对话 |
| 用户管理 | ❌ | ✅ |
| 系统配置 | ❌ | ✅ |
| 流程模板 | ❌ | ✅ |
| 用户反馈 | ❌ | ✅ |
| 计算节点 | ❌ | ✅ |
| 操作日志 | ❌ | ✅ |

## 七、安全边界说明

1. **后端是唯一安全底线**：前端路由守卫和指令只是 UX 优化，真正的权限校验在 `@PreAuthorize`。即使用户直接调用 API，无 ADMIN 角色也会被 403 拒绝。

2. **角色控制 + 数据隔离 = 完整权限**：角色控制接口访问权（"能不能进这个模块"），数据隔离控制行级可见性（"能看到哪些数据"）。两者独立但缺一不可——仅有角色控制，普通用户能互相偷看数据；仅有数据隔离，普通用户能访问管理功能。

3. **归属校验在 Service 层**：Controller 层不信任客户端传入的 userId，由 Service 层从 SecurityContext 自行获取当前用户身份，防止参数篡改。

4. **新角色扩展**：如需新增角色（如 ROLE_ANALYST），只需在 `roles` 表插入记录、在 `role_permissions` 表配置权限映射、在前端 `meta.roles` 和 `v-permission` 中加入新角色标识即可，无需改代码逻辑。

## 八、涉及文件清单

### 后端
| 文件 | 改动 |
|---|---|
| `SecurityConfig.java` | 加 `@EnableMethodSecurity` |
| `FrontUserDTO.java` | `FrontUserInfoDTO` 加 `roles` 字段 |
| `AdminAuthController.java` | `getUserInfo()` 查角色并填充 |
| `FrontAuthController.java` | `getUserInfo()` 查角色并填充 |
| `AdminSystemController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| `AdminUserController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| `AdminRoleController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| `AdminWorkflowTemplateController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| `AdminFeedbackController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| `AdminWorkerController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| `AdminLogController.java` | 类级 `@PreAuthorize("hasRole('ADMIN')")` |
| 新增 `AccessDeniedEntryPoint.java` | 403 JSON 响应 |

### 前端
| 文件 | 改动 |
|---|---|
| 新增 `src/directives/permission.ts` | `v-permission` 指令实现 |
| `src/main.ts` | 注册全局指令 |
| `src/router/index.ts` | 路由加 `meta.roles` + 守卫加角色检查 |
| `src/layout/AdminLayout.vue` | 系统管理/监控菜单加 `v-if` 角色判断 |
