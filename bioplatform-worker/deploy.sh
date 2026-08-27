#!/bin/bash
# Worker 一键部署脚本
# 用法: bash deploy-worker.sh [start|stop|restart|status|build]

set -e

WORKER_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="bioplatform-worker.jar"
PID_FILE="$WORKER_DIR/worker.pid"
LOG_FILE="$WORKER_DIR/worker.log"
JAVA="java"

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 构建
build() {
    log_info "构建 Worker JAR..."
    cd "$WORKER_DIR"
    mvn clean package -q -DskipTests
    if [ $? -eq 0 ]; then
        log_info "构建成功: $WORKER_DIR/target/$JAR_NAME"
    else
        log_error "构建失败"
        exit 1
    fi
}

# 获取 PID
get_pid() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "$pid"
            return 0
        fi
    fi
    return 1
}

# 启动
start() {
    local pid
    if pid=$(get_pid); then
        log_warn "Worker 已在运行 (PID: $pid)"
        return 0
    fi

    if [ ! -f "$WORKER_DIR/target/$JAR_NAME" ]; then
        log_warn "JAR 不存在，先构建..."
        build
    fi

    log_info "启动 Worker..."
    nohup $JAVA -Xms128m -Xmx512m -jar "$WORKER_DIR/target/$JAR_NAME" \
        --server.port=18081 \
        > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 2

    if pid=$(get_pid); then
        log_info "Worker 启动成功 (PID: $pid, 端口: 18081)"
        log_info "日志: tail -f $LOG_FILE"
    else
        log_error "Worker 启动失败，查看日志: $LOG_FILE"
        exit 1
    fi
}

# 停止
stop() {
    local pid
    if pid=$(get_pid); then
        log_info "停止 Worker (PID: $pid)..."
        kill "$pid"
        sleep 2
        if kill -0 "$pid" 2>/dev/null; then
            kill -9 "$pid"
        fi
        rm -f "$PID_FILE"
        log_info "Worker 已停止"
    else
        log_warn "Worker 未在运行"
    fi
}

# 重启
restart() {
    stop
    start
}

# 状态
status() {
    local pid
    if pid=$(get_pid); then
        log_info "Worker 运行中 (PID: $pid)"
        # 健康检查
        if command -v curl &>/dev/null; then
            local resp=$(curl -s --max-time 3 http://localhost:18081/worker/health 2>/dev/null)
            if [ -n "$resp" ]; then
                log_info "健康检查: $resp"
            else
                log_warn "健康检查失败"
            fi
        fi
    else
        log_warn "Worker 未在运行"
    fi
}

case "${1:-help}" in
    build)   build ;;
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    *)
        echo "用法: $0 {build|start|stop|restart|status}"
        echo ""
        echo "  build    构建 JAR"
        echo "  start    启动 Worker (端口 18081)"
        echo "  stop     停止 Worker"
        echo "  restart  重启 Worker"
        echo "  status   查看运行状态"
        ;;
esac
