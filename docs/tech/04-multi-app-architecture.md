# 前后端分离的多应用架构

> BioPlatform 技术文档：一套后端服务两个独立前端的架构设计。

## 架构设计

```
                    ┌──────────────────┐
                    │  Spring Boot API │
                    │   :8080          │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
     /api/admin/**    /api/front/**     /ws/**
              │              │              │
    ┌─────────▼──────┐  ┌───▼──────────┐   │
    │  bioplatform-   │  │  bioplatform- │   │
    │  admin :3000    │  │  front :3001  │   │
    └────────────────┘  └──────────────┘   │
                             │               │
                    ┌────────▼───────────────▼──┐
                    │   MySQL + Redis            │
                    └───────────────────────────┘
```

## 为什么需要两个前端？

| 维度 | 管理后台 (admin) | 用户门户 (front) |
|------|-----------------|-----------------|
| 用户角色 | 管理员 | 普通研究者 |
| 功能范围 | 全功能 | 轻量（浏览/AI对话） |
| 路由守卫 | 必须登录 | 大部分公开 |
| UI 复杂度 | 侧边栏 + 多级菜单 | 顶部导航 + 简洁布局 |
| 依赖 | 额外：WangEditor、i18n | 精简：Marked、GSAP |

## 独立的认证体系

```typescript
// admin: localStorage 直接存储
localStorage.setItem('access_token', token)

// front: pinia-plugin-persistedstate 存储
userStore.token = token  // → localStorage.bio_user
```

## 后端接口分层

```
/api/admin/**     ← 管理员专用，需要 ROLE_ADMIN
/api/front/**     ← 公开接口，部分需要登录
```

## Vite 配置

两个应用各自配置代理，指向同一个后端：

```typescript
// admin/vite.config.ts - port 3000
// front/vite.config.ts - port 3001
server: {
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true },
    '/ws':  { target: 'ws://localhost:8080', ws: true }
  }
}
```

## Docker 部署

```yaml
services:
  admin:   ports: ["8081:80"]   # nginx 托管
  front:   ports: ["80:80"]
  backend: ports: ["8080:8080"]
```
