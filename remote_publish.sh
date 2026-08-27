#!/bin/bash
# BioPlatform 远程部署脚本
# 参考博客项目: 本地构建镜像 → 导出 → 上传 → 导入 → docker compose 启动
# 用法: bash remote_publish.sh

set -e

SERVER_USER="luosg"
SERVER_IP="39.97.180.240"
SERVER_PORT="20225"
SSH_KEY="$HOME/.ssh/aliyun"
SSH="ssh -i $SSH_KEY -p $SERVER_PORT"
SCP="scp -i $SSH_KEY -P $SERVER_PORT"

GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

cd "$(dirname "$0")"

# 1. 构建
log_info "构建后端 JAR..."
cd bioplatform-springboot && mvn clean package -q -DskipTests && cd ..

log_info "构建 Docker 镜像..."
docker compose build

# 2. 导出上传
log_info "导出镜像..."
docker save bioplatform-backend bioplatform-front bioplatform-admin -o /tmp/bioplatform-images.tar
log_info "大小: $(du -h /tmp/bioplatform-images.tar | cut -f1)"

log_info "上传到服务器..."
$SCP /tmp/bioplatform-images.tar ${SERVER_USER}@${SERVER_IP}:/tmp/
$SCP docker-compose-remote.yml ${SERVER_USER}@${SERVER_IP}:/tmp/

# 3. 远程部署
log_info "远程部署..."
$SSH bash -s << 'REMOTE'
set -e
GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

REMOTE_DIR="/home/luosg/bioplatform"
mkdir -p $REMOTE_DIR
cp /tmp/docker-compose-remote.yml $REMOTE_DIR/

# 导入镜像
log_info "导入镜像..."
docker load -i /tmp/bioplatform-images.tar
rm -f /tmp/bioplatform-images.tar /tmp/docker-compose-remote.yml

# 停旧容器
cd $REMOTE_DIR
docker compose -f docker-compose-remote.yml down 2>/dev/null || true

# 启动
log_info "启动服务..."
docker compose -f docker-compose-remote.yml up -d

sleep 5
docker compose -f docker-compose-remote.yml ps
log_info "部署完成!"
REMOTE

rm -f /tmp/bioplatform-images.tar
log_info "全部完成!"
