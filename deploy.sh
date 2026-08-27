#!/bin/bash
# BioPlatform 一键部署脚本
# 参考博客项目部署方式，共用 MySQL/Redis，加入 blog_net 网络
# 用法: bash deploy.sh [build|deploy|restart|logs|status]

set -e

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
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============ 构建 ============
build() {
    log_info "构建后端 JAR..."
    cd "$PROJECT_DIR/bioplatform-springboot"
    mvn clean package -q -DskipTests

    log_info "构建 Docker 镜像..."
    cd "$PROJECT_DIR"
    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
    log_info "镜像构建完成"
}

# ============ 部署 ============
deploy() {
    build

    # 导出上传
    log_info "导出并上传镜像..."
    docker save ${IMAGE_NAME}:${IMAGE_TAG} -o ${TAR_FILE}
    ${SCP_CMD} ${TAR_FILE} ${SERVER_USER}@${SERVER_IP}:/tmp/

    # 上传 SQL
    log_info "上传数据库脚本..."
    ${SCP_CMD} "$PROJECT_DIR/database/bioplatform.sql" ${SERVER_USER}@${SERVER_IP}:/tmp/

    # 远程执行
    log_info "服务器部署..."
    ${SSH_CMD} bash -s << 'REMOTE'
set -e

IMAGE_NAME="bioplatform-backend"
IMAGE_TAG="latest"
CONTAINER_NAME="bioplatform-backend"
TAR_FILE="/tmp/bioplatform-backend.tar"
SQL_FILE="/tmp/bioplatform.sql"
MYSQL_CONTAINER="blog_mysql"
MYSQL_PASS="REDACTED_DB_PASSWORD"
DB_NAME="bioplatform"

GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }

# 1. 导入镜像
log_info "导入镜像..."
docker load -i ${TAR_FILE}
rm -f ${TAR_FILE}

# 2. 建库
log_info "创建数据库..."
docker exec ${MYSQL_CONTAINER} mysql -uroot -p${MYSQL_PASS} \
    -e "CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

# 3. 导入表结构+初始数据
log_info "导入表结构..."
docker exec -i ${MYSQL_CONTAINER} mysql -uroot -p${MYSQL_PASS} ${DB_NAME} < ${SQL_FILE} 2>/dev/null || true
rm -f ${SQL_FILE}

# 4. 停旧容器
docker rm -f ${CONTAINER_NAME} 2>/dev/null || true

# 5. 启动（加入 blog_net 网络，用 blog_mysql:3306 连接）
log_info "启动容器..."
docker run -d \
    --name ${CONTAINER_NAME} \
    --restart unless-stopped \
    --network blog_net \
    -p 8083:8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SPRING_DATASOURCE_URL="jdbc:mysql://blog_mysql:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
    -e SPRING_DATASOURCE_USERNAME=root \
    -e SPRING_DATASOURCE_PASSWORD="${MYSQL_PASS}" \
    -v bioplatform-uploads:/app/uploads \
    ${IMAGE_NAME}:${IMAGE_TAG}

# 6. 等待启动
log_info "等待启动..."
sleep 8

# 7. 检查
if docker ps --filter name=${CONTAINER_NAME} --format '{{.Status}}' | grep -q "Up"; then
    log_info "✅ 部署成功!"
    log_info "访问: http://$(curl -s ifconfig.me 2>/dev/null || echo '39.97.180.240'):8083"
else
    echo "启动可能失败，查看日志:"
    docker logs --tail 30 ${CONTAINER_NAME}
fi
REMOTE

    rm -f ${TAR_FILE}
}

# ============ 其他命令 ============
restart() { ${SSH_CMD} "docker restart ${CONTAINER_NAME}"; log_info "已重启"; }
logs()    { ${SSH_CMD} "docker logs -f --tail 50 ${CONTAINER_NAME}"; }
status()  { ${SSH_CMD} "docker ps --filter name=${CONTAINER_NAME}"; }

case "${1:-help}" in
    build)   build ;;
    deploy)  deploy ;;
    restart) restart ;;
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
