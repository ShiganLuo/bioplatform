#!/usr/bin/env bash
# 导出 Docker 命名卷为 tar.gz 并传回本地(aliyun → 本地)
# 自动剥离 "proxy off" 噪声 + md5 端到端校验
#
# 用法:
#   ./02_export_volume.sh                       # 默认 bioplatform-uploads 卷
#   VOLUME=other-data ./02_export_volume.sh
# 环境变量(可选):
#   SRC_SSH=aliyun
#   VOLUME=bioplatform-uploads
#   OUT_DIR=/tmp
set -euo pipefail

SRC_SSH="${SRC_SSH:-aliyun}"
VOLUME="${VOLUME:-bioplatform-uploads}"
OUT_DIR="${OUT_DIR:-/tmp}"
REMOTE_TAR="/tmp/${VOLUME}_export.tar.gz"
LOCAL_TAR="$OUT_DIR/${VOLUME}_export.tar.gz"

# 1. 源端打包:tar 必须落到宿主机文件(-czf - 重定向到宿主机)。
#    写法坑: tar -czf /tmp/x.tar.gz 在 --rm 容器里执行会随容器销毁,文件不存在。
REMOTE_MD5=$(ssh "$SRC_SSH" \
    "docker run --rm -v $VOLUME:/v alpine tar -C /v -czf - . > $REMOTE_TAR; \
     gzip -t $REMOTE_TAR && md5sum $REMOTE_TAR" 2>/dev/null \
    | grep -oE '^[0-9a-f]{32}' | head -1)
[ -n "$REMOTE_MD5" ] || { echo "FAIL: 源端打包失败"; exit 1; }
echo "源端 md5=$REMOTE_MD5"

# 2. cat 管道传回本地。不用 scp:本环境 SSH stdout 被污染,scp 报
#    "Received message too long";也不用 ssh远程cat直接管道二进制不加处理。
ssh "$SRC_SSH" "cat $REMOTE_TAR" > "$LOCAL_TAR" 2>/dev/null

# 3. 剥 "proxy off\n" 前缀 + md5 比对 + gzip 完整性
python3 - "$LOCAL_TAR" "$REMOTE_MD5" <<'EOF'
import sys, hashlib, gzip
p, expect = sys.argv[1], sys.argv[2]
d = open(p, 'rb').read()
if d.startswith(b'proxy off\n'):
    d = d[len(b'proxy off\n'):]
    open(p, 'wb').write(d)
    print('stripped 9 junk bytes (proxy off 前缀)')
m = hashlib.md5(d).hexdigest()
if m != expect:
    print(f'FAIL: md5 不一致 local={m} remote={expect}')
    sys.exit(1)
try:
    gzip.decompress(d)
except Exception as e:
    print(f'FAIL: gzip 解压失败 {e}')
    sys.exit(1)
print(f'OK: md5 一致 + gzip 完整 {m}')
EOF

echo "产物: $LOCAL_TAR"
