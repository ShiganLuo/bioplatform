# ============================================================
# 生物信息学云平台 - 管理后台前端 Dockerfile
# 多阶段构建（Multi-stage Build）
# 阶段1：使用 Node.js 构建 Vue3 项目
# 阶段2：使用 Nginx 提供静态文件服务
# ============================================================

# ==================== 阶段1：构建阶段 ====================
# 使用 Node.js 20 Alpine 镜像构建前端项目
FROM node:20-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制 package.json 和 package-lock.json（如果存在）
# 先复制依赖文件，利用 Docker 缓存加速 npm install
COPY package.json ./
RUN if [ -f package-lock.json ]; then cp package-lock.json .; fi

# 安装项目依赖
# --prefer-offline: 优先使用缓存的包
# --no-audit: 跳过安全审计（构建时不需要）
RUN npm install --prefer-offline --no-audit

# 复制所有源代码
COPY . .

# 执行构建，生成生产环境静态文件
# 输出到 dist 目录
RUN npm run build

# ==================== 阶段2：运行阶段 ====================
# 使用 Nginx Alpine 镜像提供静态文件服务
FROM nginx:alpine

# 维护者信息
LABEL maintainer="bioplatform"
LABEL description="生物信息学云平台 - 管理后台前端"

# 删除默认的 Nginx 配置
RUN rm -f /etc/nginx/conf.d/default.conf

# 复制自定义 Nginx 配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 从构建阶段复制构建好的静态文件到 Nginx 默认站点目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 暴露 HTTP 端口
EXPOSE 80

# 启动 Nginx（前台运行，容器退出时自动停止）
CMD ["nginx", "-g", "daemon off;"]
