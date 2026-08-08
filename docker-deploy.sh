#!/bin/bash
# ============================================================
# 生物信息学云平台 - Docker 部署脚本
# 用法：./docker-deploy.sh [command]
# 命令：
#   start   - 启动所有服务（后台运行）
#   stop    - 停止所有服务
#   restart - 重启所有服务
#   rebuild - 重新构建所有镜像并启动
#   logs    - 查看服务日志
#   status  - 查看服务状态
#   init    - 首次初始化（构建+启动+初始化数据库）
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 切换到项目根目录
cd "$(dirname "$0")"

# 启动所有服务
start_services() {
    print_info "启动生物信息学云平台所有服务..."
    docker compose up -d
    print_success "所有服务已启动"
    echo ""
    print_info "服务访问地址："
    echo "  - 管理后台：http://localhost:8081"
    echo "  - 公开前端：http://localhost:80"
    echo "  - 后端API：http://localhost:8080"
    echo "  - API文档：http://localhost:8080/doc.html"
    echo "  - MySQL：localhost:3308"
    echo "  - Redis：localhost:6380"
}

# 停止所有服务
stop_services() {
    print_info "停止所有服务..."
    docker compose down
    print_success "所有服务已停止"
}

# 重启所有服务
restart_services() {
    print_info "重启所有服务..."
    docker compose restart
    print_success "所有服务已重启"
}

# 重新构建并启动
rebuild_services() {
    print_info "重新构建所有镜像..."
    docker compose build --no-cache
    print_info "启动所有服务..."
    docker compose up -d
    print_success "重新构建并启动完成"
}

# 查看日志
show_logs() {
    if [ -n "$2" ]; then
        docker compose logs -f "$2"
    else
        docker compose logs -f
    fi
}

# 查看服务状态
show_status() {
    print_info "服务状态："
    docker compose ps
    echo ""
    print_info "容器资源使用："
    docker stats --no-stream $(docker compose ps -q) 2>/dev/null || true
}

# 首次初始化
init_services() {
    print_info "首次初始化部署..."
    echo ""

    # 检查 Docker 是否安装
    if ! command -v docker &> /dev/null; then
        print_error "Docker 未安装，请先安装 Docker"
        exit 1
    fi

    # 检查 Docker Compose 是否安装
    if ! command -v docker compose &> /dev/null; then
        print_error "Docker Compose 未安装，请先安装 Docker Compose"
        exit 1
    fi

    # 创建上传目录
    print_info "创建文件上传目录..."
    mkdir -p /home/luosg/uploads/bioplatform

    # 构建镜像
    print_info "构建 Docker 镜像..."
    docker compose build

    # 启动服务
    print_info "启动所有服务..."
    docker compose up -d

    # 等待 MySQL 就绪
    print_info "等待 MySQL 服务就绪..."
    sleep 30

    print_success "初始化部署完成！"
    echo ""
    print_info "服务访问地址："
    echo "  - 管理后台：http://localhost:8081"
    echo "  - 公开前端：http://localhost:80"
    echo "  - 后端API：http://localhost:8080"
    echo "  - API文档：http://localhost:8080/doc.html"
    echo "  - MySQL：localhost:3308（密码：bioplatform123）"
    echo "  - Redis：localhost:6380"
    echo ""
    print_warning "请修改 docker compose.yml 中的数据库密码和JWT密钥（生产环境）"
}

# 显示帮助
show_help() {
    echo "生物信息学云平台 - Docker 部署脚本"
    echo ""
    echo "用法：$0 [command]"
    echo ""
    echo "可用命令："
    echo "  start     启动所有服务（后台运行）"
    echo "  stop      停止所有服务"
    echo "  restart   重启所有服务"
    echo "  rebuild   重新构建所有镜像并启动"
    echo "  logs      查看服务日志（可指定服务名：$0 logs backend）"
    echo "  status    查看服务状态"
    echo "  init      首次初始化（构建+启动+初始化数据库）"
    echo "  help      显示此帮助信息"
}

# 主入口
case "${1:-help}" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    rebuild)
        rebuild_services
        ;;
    logs)
        show_logs "$@"
        ;;
    status)
        show_status
        ;;
    init)
        init_services
        ;;
    help|*)
        show_help
        ;;
esac
