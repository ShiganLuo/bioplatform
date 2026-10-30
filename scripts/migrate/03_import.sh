#!/usr/bin/env bash
# 在目标服务器执行:导入 dump + 恢复 uploads 卷 + 迁移后校验
# (在目标机本地运行,或 ssh 目标机执行;前置:01/02 产物 + .env 已传到目标机)
#
# 用法:
#   ./03_import.sh /home/luosg/bioplatform-migrate/bioplatform_dump.sql \
#                  /home/luosg/bioplatform-migrate/bioplatform-uploads_export.tar.gz
# 环境变量(可选):
#   DB=bioplatform                  数据库名(dump 内含 --databases,此处仅用于校验查询)
#   MYSQL_CONTAINER=blog_mysql
#   UPLOADS_VOLUME=bioplatform-uploads
#   ENV_FILE=/home/luosg/bioplatform/.env    含 DB_PASSWORD= 的文件
set -euo pipefail

DUMP="${1:?用法: $0 <dump.sql> <uploads.tar.gz>}"
TARBALL="${2:?用法: $0 <dump.sql> <uploads.tar.gz>}"
DB="${DB:-bioplatform}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-blog_mysql}"
UPLOADS_VOLUME="${UPLOADS_VOLUME:-bioplatform-uploads}"
ENV_FILE="${ENV_FILE:-/home/luosg/bioplatform/.env}"

PW=$(grep '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2)
[ -n "$PW" ] || { echo "FAIL: $ENV_FILE 里没有 DB_PASSWORD"; exit 1; }

# 1. uploads 卷恢复(卷不存在则建;tar 内容覆盖解包,权限差异可忽略)
docker volume create "$UPLOADS_VOLUME" >/dev/null 2>&1 || true
docker run --rm -v "$UPLOADS_VOLUME":/v -v "$(dirname "$TARBALL")":/m alpine \
    tar -xzf "/m/$(basename "$TARBALL")" -C /v
echo "OK: $UPLOADS_VOLUME 卷已解包"

# 2. 等 MySQL 初始化/健康(新容器首次启动要跑 init 脚本,给足时间)
for _ in $(seq 1 60); do
    H=$(docker inspect "$MYSQL_CONTAINER" --format '{{.State.Health.Status}}' 2>/dev/null || echo missing)
    [ "$H" = "healthy" ] && break
    sleep 3
done
[ "$H" = "healthy" ] || { echo "FAIL: $MYSQL_CONTAINER 未就绪(status=$H)"; exit 1; }
echo "OK: $MYSQL_CONTAINER healthy"

# 3. 导入 dump。-i 必须有(读 stdin);MYSQL_PWD 走环境变量,密码不进命令行
docker exec -i -e MYSQL_PWD="$PW" "$MYSQL_CONTAINER" mysql -uroot \
    --default-character-set=utf8mb4 < "$DUMP"
echo "OK: IMPORT_DONE"

# 4. 校验:表数 + 关键表行数(与源端比对)
docker exec -e MYSQL_PWD="$PW" "$MYSQL_CONTAINER" mysql -uroot \
    --default-character-set=utf8mb4 -e "
        SELECT COUNT(*) AS table_count FROM information_schema.tables
          WHERE table_schema='$DB';
        SELECT COUNT(*) AS users FROM $DB.users;
        SELECT COUNT(*) AS cfg  FROM $DB.system_configs;"

# 5. uploads 卷抽查
docker run --rm -v "$UPLOADS_VOLUME":/v alpine sh -c 'ls -la /v | head'

cat <<'EOF'

后续必做(见 docs/DB_MIGRATION_2026-10-29.md『迁移后清理』):
  1) 禁用指向旧环境的计算节点(否则 WorkerRegistry 持续健康检查报错刷屏):
     UPDATE bioplatform.compute_nodes SET status=0, healthy=0 WHERE url LIKE '%<旧worker端口>%';
  2) 重启 backend(节点注册表在内存,DB 改完必须重启才生效)
  3) 验证: front/admin 200 + title 正确、POST /api/front/auth/login 返回 JSON
     "用户不存在"(证明 DB 链路通)、后端日志 0 ERROR
EOF
