#!/bin/bash
# BioPlatform 远程部署脚本
# 参考博客项目 remote_publish.sh: 本地构建镜像 → 导出 → 上传 → 导入 → 启动
# 用法: bash remote_publish.sh

set -e

# ============ 配置 ============
SERVER_USER="luosg"
SERVER_IP="39.97.180.240"
SERVER_PORT="20225"
SSH_KEY="$HOME/.ssh/aliyun"
SSH="ssh -i $SSH_KEY -p $SERVER_PORT"
SCP="scp -i $SSH_KEY -P $SERVER_PORT"
REMOTE_DIR="/home/luosg/bioplatform"

GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

cd "$(dirname "$0")"

# ============ 1. 本地构建镜像 ============
log_info "构建后端 JAR..."
cd bioplatform-springboot && mvn clean package -q -DskipTests && cd ..

log_info "构建 Docker 镜像..."
docker compose build

# ============ 2. 导出镜像 ============
log_info "导出镜像..."
docker save bioplatform-backend bioplatform-front bioplatform-admin -o /tmp/bioplatform-images.tar
log_info "镜像大小: $(du -h /tmp/bioplatform-images.tar | cut -f1)"

# ============ 3. 上传到服务器 ============
log_info "上传镜像..."
$SCP /tmp/bioplatform-images.tar ${SERVER_USER}@${SERVER_IP}:/tmp/

log_info "上传配置文件..."
$SCP nginx-proxy.conf database/bioplatform.sql ${SERVER_USER}@${SERVER_IP}:/tmp/

# ============ 4. 远程部署 ============
log_info "远程部署..."
$SSH bash -s << 'REMOTE'
set -e
GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

REMOTE_DIR="/home/luosg/bioplatform"
mkdir -p $REMOTE_DIR

# 导入镜像
log_info "导入镜像..."
docker load -i /tmp/bioplatform-images.tar
rm -f /tmp/bioplatform-images.tar

# 停旧容器
docker rm -f bioplatform-backend bioplatform-front bioplatform-admin 2>/dev/null || true

# 启动后端
log_info "启动后端..."
docker run -d \
    --name bioplatform-backend \
    --restart unless-stopped \
    --network blog_net \
    -p 8083:8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SPRING_DATASOURCE_URL='jdbc:mysql://blog_mysql:3306/bioplatform?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
    -e SPRING_DATASOURCE_USERNAME=root \
    -e SPRING_DATASOURCE_PASSWORD=REDACTED_DB_PASSWORD \
    -v bioplatform-uploads:/app/uploads \
    bioplatform-backend

# 启动前台
log_info "启动前台..."
docker run -d \
    --name bioplatform-front \
    --restart unless-stopped \
    --network blog_net \
    -p 3000:80 \
    bioplatform-front

# 启动后台
log_info "启动后台..."
docker run -d \
    --name bioplatform-admin \
    --restart unless-stopped \
    --network blog_net \
    -p 3001:80 \
    bioplatform-admin

# 清理
rm -f /tmp/bioplatform.conf /tmp/bioplatform.sql

log_info "部署完成!"
docker ps --filter name=bioplatform --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
REMOTE

rm -f /tmp/bioplatform-images.tar
log_info "全部完成!"
