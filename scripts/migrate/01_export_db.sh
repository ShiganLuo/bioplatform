#!/usr/bin/env bash
# 从源服务器 MySQL 容器导出数据库 dump(aliyun → 本地)
# 自动剥离 SSH stdout 噪声("proxy off" 前缀),这是本环境的必踩坑
#
# 用法:
#   ./01_export_db.sh                  # 默认导出 bioplatform 库
#   ./01_export_db.sh blog             # 导出 blog 库
# 环境变量(可选):
#   SRC_SSH=aliyun          源服务器 ssh 别名
#   MYSQL_CONTAINER=blog_mysql
#   OUT_DIR=/tmp            本地输出目录
set -euo pipefail

SRC_SSH="${SRC_SSH:-aliyun}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-blog_mysql}"
OUT_DIR="${OUT_DIR:-/tmp}"
DB="${1:-bioplatform}"
DUMP="$OUT_DIR/${DB}_dump.sql"

# 1. 取 root 密码:从容器 env 读,不打印、不写进命令行(防 ps 泄漏)
PW=$(ssh "$SRC_SSH" "docker exec $MYSQL_CONTAINER printenv MYSQL_ROOT_PASSWORD" 2>/dev/null | tail -1)
[ -n "$PW" ] || { echo "FAIL: 取不到 $MYSQL_CONTAINER 的 MYSQL_ROOT_PASSWORD"; exit 1; }

# 2. mysqldump 管道直出到本地文件
#    --single-transaction: 不锁表(InnoDB 在线导出)
#    --routines --triggers: 存储过程/触发器一起走
#    --databases: dump 内含 CREATE DATABASE/USE,导入端无需预建库
ssh "$SRC_SSH" "docker exec -e MYSQL_PWD='$PW' $MYSQL_CONTAINER mysqldump -uroot \
    --single-transaction --routines --triggers \
    --default-character-set=utf8mb4 --databases $DB" 2>/dev/null > "$DUMP"

# 3. 剥首部噪声(源机 .bashrc 会往 stdout 打 "proxy off\n"),定位 mysqldump 头
python3 - "$DUMP" <<'EOF'
import sys
p = sys.argv[1]
data = open(p, 'rb').read()
idx = data.find(b'-- MySQL dump')
if idx == -1:
    print('FAIL: 找不到 mysqldump 头,传输被污染或 dump 失败')
    sys.exit(1)
if idx:
    open(p, 'wb').write(data[idx:])
    print(f'stripped {idx} junk bytes (proxy off 前缀)')
EOF

# 4. 完整性校验:mysqldump 正常结尾 + 表数量
grep -q 'Dump completed' "$DUMP" || { echo "FAIL: dump 不完整(结尾无 Dump completed)"; exit 1; }
TABLES=$(grep -c 'CREATE TABLE' "$DUMP" || true)
SIZE=$(stat -c%s "$DUMP")
MD5=$(md5sum "$DUMP" | cut -d' ' -f1)

echo "OK: $DUMP"
echo "    size=$SIZE  tables=$TABLES  md5=$MD5"
