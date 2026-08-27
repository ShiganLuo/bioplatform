#!/bin/bash
# BioPlatform 一键部署脚本
# 用法: bash deploy.sh [build|deploy|restart|logs|status]
# 前提: 服务器已安装 Docker, SSH key 已配置

set -e

# ============ 配置 ============
SERVER_USER="luosg"
SERVER_IP="39.97.180.240"
SERVER_PORT="20225"
SSH_KEY="$HOME/.ssh/aliyun"
SSH_CMD="ssh -i $SSH_KEY -p $SERVER_PORT ${SERVER_USER}@${SERVER_IP}"
SCP_CMD="scp -i $SSH_KEY -P $SERVER_PORT"

IMAGE_NAME="bioplatform-backend"
IMAGE_TAG="latest"
CONTAINER_NAME="bioplatform-backend"
TAR_FILE="/tmp/${IMAGE_NAME}.tar"

# MySQL 配置（与博客项目共用）
MYSQL_CONTAINER="blog_mysql"
MYSQL_ROOT_PASS="REDACTED_DB_PASSWORD"
MYSQL_PORT="3308"
DB_NAME="bioplatform"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============ 本地构建 JAR ============
build_jar() {
    log_info "构建后端 JAR..."
    cd "$PROJECT_DIR/bioplatform-springboot"
    mvn clean package -q -DskipTests
    log_info "JAR 构建成功: $(ls target/*.jar)"
}

# ============ 构建 Docker 镜像 ============
build_image() {
    build_jar
    log_info "构建 Docker 镜像 ${IMAGE_NAME}:${IMAGE_TAG}..."
    cd "$PROJECT_DIR"
    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
    log_info "镜像构建完成"
}

# ============ 导出镜像 ============
export_image() {
    log_info "导出镜像为 tar..."
    docker save ${IMAGE_NAME}:${IMAGE_TAG} -o ${TAR_FILE}
    log_info "镜像已导出: ${TAR_FILE} ($(du -h ${TAR_FILE} | cut -f1))"
}

# ============ 上传到服务器 ============
upload_image() {
    export_image
    log_info "上传镜像到服务器..."
    ${SCP_CMD} ${TAR_FILE} ${SERVER_USER}@${SERVER_IP}:/tmp/
    log_info "上传完成"
}

# ============ 服务器部署 ============
remote_deploy() {
    log_info "服务器部署..."
    ${SSH_CMD} bash -s << 'REMOTE_SCRIPT'
set -e

IMAGE_NAME="bioplatform-backend"
IMAGE_TAG="latest"
CONTAINER_NAME="bioplatform-backend"
TAR_FILE="/tmp/${IMAGE_NAME}.tar"

GREEN='\033[0;32m'
NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

# 1. 导入镜像
log_info "导入镜像..."
docker load -i ${TAR_FILE}

# 2. 删除旧容器（如有）
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_info "停止旧容器..."
    docker stop ${CONTAINER_NAME} 2>/dev/null || true
    docker rm ${CONTAINER_NAME} 2>/dev/null || true
fi

# 3. 启动新容器
log_info "启动容器..."
docker run -d \
    --name ${CONTAINER_NAME} \
    --restart unless-stopped \
    -p 8083:8080 \
    --add-host=host.docker.internal:host-gateway \
    -v bioplatform-uploads:/app/uploads \
    ${IMAGE_NAME}:${IMAGE_TAG}

# 4. 等待启动
log_info "等待启动..."
sleep 5

# 5. 健康检查
if curl -sf --max-time 5 http://localhost:8083/api/front/site-config > /dev/null 2>&1; then
    log_info "✅ 部署成功! 端口: 8083"
else
    log_info "启动中，查看日志: docker logs -f ${CONTAINER_NAME}"
fi

# 6. 清理
rm -f ${TAR_FILE}
REMOTE_SCRIPT
}

# ============ 初始化数据库 ============
init_db() {
    log_info "初始化数据库..."
    # 导出本地 SQL
    local sql_file="/tmp/bioplatform-init.sql"
    docker exec bioplatform-mysql mysqldump -uroot -pbioplatform123 --no-data bioplatform > ${sql_file} 2>/dev/null || true

    # 上传到服务器
    ${SCP_CMD} ${sql_file} ${SERVER_USER}@${SERVER_IP}:/tmp/

    # 服务器执行
    ${SSH_CMD} "docker exec -i ${MYSQL_CONTAINER} mysql -uroot -p${MYSQL_ROOT_PASS} -e 'CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;' && cat /tmp/bioplatform-init.sql | docker exec -i ${MYSQL_CONTAINER} mysql -uroot -p${MYSQL_ROOT_PASS} ${DB_NAME}" 2>&1 | grep -v "Warning"

    # 导入数据
    ${SCP_CMD} "$PROJECT_DIR/database/bioplatform.sql" ${SERVER_USER}@${SERVER_IP}:/tmp/
    ${SSH_CMD} "cat /tmp/bioplatform.sql | docker exec -i ${MYSQL_CONTAINER} mysql -uroot -p${MYSQL_ROOT_PASS} ${DB_NAME}" 2>&1 | grep -v "Warning"

    rm -f ${sql_file}
    log_info "数据库初始化完成"
}

# ============ 一键全部执行 ============
deploy_all() {
    build_image
    upload_image
    init_db
    remote_deploy
    log_info "🎉 全部完成! 访问: http://${SERVER_IP}:8083"
}

# ============ 查看日志 ============
logs() {
    ${SSH_CMD} "docker logs -f --tail 50 ${CONTAINER_NAME}"
}

# ============ 查看状态 ============
status() {
    ${SSH_CMD} "docker ps --filter name=${CONTAINER_NAME}"
}

case "${1:-help}" in
    build)   build_image ;;
    deploy)  deploy_all ;;
    restart)
        ${SSH_CMD} "docker restart ${CONTAINER_NAME}"
        log_info "已重启"
        ;;
    logs)    logs ;;
    status)  status ;;
    *)
        echo "用法: $0 {build|deploy|restart|logs|status}"
        echo ""
        echo "  build    本地构建 JAR + Docker 镜像"
        echo "  deploy   一键部署（构建+上传+建库+启动）"
        echo "  restart  重启远程容器"
        echo "  logs     查看远程日志"
        echo "  status   查看远程容器状态"
        ;;
esac
