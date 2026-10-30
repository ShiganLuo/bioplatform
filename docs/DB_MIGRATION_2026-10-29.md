# 数据库迁移记录:aliyun → lv_zheda(2026-10-29)

bioplatform 全栈从阿里云公网服务器迁到 lv_zheda 内网服务器,本文档记录数据库 +
文件存储的真实迁移步骤、踩坑与验证结果。脚本见 `scripts/migrate/`,与本文档
的步骤一一对应。

## 一、拓扑与迁移范围

```
阿里云(公网入口,仅留 nginx-proxy + frps)
  blog_mysql (mysql:8.0.33, 卷: 宿主机目录)
  ──SSH(VPN 直连)──►  lv_zheda(内网, <LV_ZHEDA_IP>:20225)
                        blog_mysql (新容器, 命名卷 blog-mysql-data)
                        blog_redis (新容器, 命名卷 blog-redis-data)
                        bioplatform-uploads (命名卷)
                        backend/front/admin (compose 栈)
```

迁移清单:

| 项目 | 方式 | 规模 |
|---|---|---|
| bioplatform 库 | mysqldump 全量 | 513K, 20 表 |
| uploads 文件 | docker 卷 tar 导出/解包 | 12KB(33 项目目录 + _chunks) |
| .env 密钥 | cat 管道 + md5 校验 | 5 个键 |
| 镜像 ×3 | docker save \| cat \| ssh docker load | backend 419MB(clean) |

前置:lv_zheda 已有 `blog_net` 网络;MySQL/Redis 复用与博客同一容器
(`blog_mysql` / `blog_redis`,bioplatform 库与 blog 库同实例不同 schema)。

## 二、导出(aliyun → 本地)

### 1. 数据库 dump — `scripts/migrate/01_export_db.sh`

```bash
# 核心命令(脚本已封装)
PW=$(ssh aliyun 'docker exec blog_mysql printenv MYSQL_ROOT_PASSWORD' | tail -1)
ssh aliyun "docker exec -e MYSQL_PWD='$PW' blog_mysql mysqldump -uroot \
    --single-transaction --routines --triggers \
    --default-character-set=utf8mb4 --databases bioplatform" \
    > /tmp/bioplatform_dump.sql
```

要点:
- `--single-transaction` 在线导出不锁表;`--databases` 让 dump 自带
  `CREATE DATABASE`/`USE`,导入端无需预建库。
- 密码从容器 env 读(`printenv`),用 `MYSQL_PWD` 环境变量传给 mysqldump,
  不出现在命令行参数里(防 `ps` 泄漏)。
- **dump 首行必然混入 `proxy off\n`**(源机 .bashrc 的输出走 SSH stdout),
  脚本自动剥掉定位到 `-- MySQL dump` 头,并校验结尾有 `Dump completed`。

### 2. uploads 卷 — `scripts/migrate/02_export_volume.sh`

```bash
# 源端打包:tar 必须重定向到宿主机文件
ssh aliyun 'docker run --rm -v bioplatform-uploads:/v alpine \
    tar -C /v -czf - . > /tmp/uploads_export.tar.gz; \
    gzip -t /tmp/uploads_export.tar.gz && md5sum /tmp/uploads_export.tar.gz'
# 传回本地(scp 不可用,见坑 2)
ssh aliyun 'cat /tmp/uploads_export.tar.gz' > /tmp/uploads_export.tar.gz
```

### 3. .env

```bash
ssh aliyun 'cat /home/luosg/bioplatform/.env > /tmp/bio_env_raw.txt && md5sum /tmp/bio_env_raw.txt'
ssh aliyun 'cat /tmp/bio_env_raw.txt' > /tmp/bio_env_raw.txt
# 剥 "proxy off\n" 后 md5 应与源端一致;5 键: DB_PASSWORD/REDIS_PASSWORD/
# SMTP_PASSWORD/JWT_SECRET/BIOPLATFORM_AES_KEY
```

## 三、导入(lv_zheda)

### 1. 目标端目录与基础设施

```bash
ssh lv_zheda 'mkdir -p /home/luosg/bioplatform-migrate \
    /home/luosg/bioplatform \
    /home/luosg/bioplatform-infra/redis /home/luosg/bioplatform-infra/mysql'
# 传输(dump/uploads tar/.env/docker-compose-remote.yml/redis.conf/low-memory.cnf)
# 均走 cat 管道,逐文件传、逐文件 md5 比对 —— 组合命令会被安全确认拦截拆开执行
```

`low-memory.cnf`(MySQL 低内存调参)与 `redis.conf`(requirepass)从 aliyun
原样拷贝,挂载路径与 aliyun 容器一致。

### 2. 起 MySQL/Redis 容器

```bash
docker volume create blog-mysql-data; docker volume create blog-redis-data

# Redis 必须带启动命令,否则只挂 conf 文件 redis 会用默认配置(无密码)
docker run -d --name blog_redis --network blog_net \
    -v /home/luosg/bioplatform-infra/redis/redis.conf:/usr/local/etc/redis/redis.conf \
    -v blog-redis-data:/data \
    redis:7.2.11 redis-server /usr/local/etc/redis/redis.conf

PW=$(grep '^DB_PASSWORD=' /home/luosg/bioplatform/.env | cut -d= -f2)
docker run -d --name blog_mysql --network blog_net \
    -e MYSQL_ROOT_PASSWORD="$PW" \
    --health-cmd="mysqladmin ping -h localhost" --health-interval=10s \
    --health-timeout=5s --health-retries=10 \
    -v /home/luosg/bioplatform-infra/mysql/low-memory.cnf:/etc/mysql/conf.d/low-memory.cnf \
    -v blog-mysql-data:/var/lib/mysql \
    mysql:8.0.33
```

### 3. 恢复数据 — `scripts/migrate/03_import.sh`

```bash
# uploads 卷解包(在目标机)
docker run --rm -v bioplatform-uploads:/v -v /home/luosg/bioplatform-migrate:/m \
    alpine tar -xzf /m/uploads_export.tar.gz -C /v

# 等 healthy 后导入 dump
for i in $(seq 1 30); do
  H=$(docker inspect blog_mysql --format '{{.State.Health.Status}}')
  [ "$H" = healthy ] && break; sleep 3
done
docker exec -i -e MYSQL_PWD="$PW" blog_mysql mysql -uroot \
    --default-character-set=utf8mb4 < /home/luosg/bioplatform-migrate/bioplatform.sql
```

### 4. 起应用栈

```bash
cd /home/luosg/bioplatform && docker compose -f docker-compose-remote.yml up -d
```

## 四、迁移后清理(必做)

**compute_nodes 指向旧环境的 worker 会导致 WorkerRegistry 持续报
`计算节点健康检查失败 ... host.docker.internal:18081`。**该注册表一半在 DB、
一半在 JVM 内存,所以两步都要做:

```sql
UPDATE bioplatform.compute_nodes SET status=0, healthy=0
 WHERE url LIKE '%18081%';   -- 旧 worker 端口,按实际情况改
```

然后重启 backend(仅改 DB 不够,内存注册表不清不会停)。

## 五、验证记录(2026-10-29 实测)

| 检查项 | 结果 |
|---|---|
| 导入后表数 | 20 张,与源端一致 |
| users / system_configs | 3 行 / 33 行,与源端一致 |
| uploads 卷 | `33/` + `_chunks/` 目录就位,backend 容器内 `/app/uploads` 可见 |
| front :3000 | 200, `<title>生信云平台` |
| admin :3001 | 200, `<title>生物信息学云平台 - 管理后台` |
| SPA 回退 | 3000/3001 随机路径均 200 |
| DB 链路 | `POST /api/front/auth/login` 返回 `{"code":400,"message":"用户不存在"}`(探针用户,证明 SQL 真被执行) |
| 后端日志 | compute_nodes 清理+重启后 0 ERROR(JWT 非 Base64 WARN 为已知无害) |

文件级校验:dump 剥噪后 `Dump completed` 结尾 + 20 个 CREATE TABLE;
uploads tar 剥噪后 md5 与源端一致 + gzip 完整;6 个文件(dump/tar/.env/
compose/redis.conf/low-memory.cnf)远端 md5 全部与本地一致。

## 六、踩坑记录(每个都真实中过招)

1. **aliyun SSH stdout 必带 `proxy off\n` 前缀**——`.bashrc` 的输出走 stdout
   而非 stderr,`mysqldump` 管道、`cat 文件` 回传的**二进制 tar 和 .env 全部
   首行污染**,md5 不匹配的元凶。所有产物先剥前缀再校验(脚本已自动处理)。
   scp 因同因报 `Received message too long`,本环境不可用,一律 `cat | ssh` 管道。
2. **tar 写进 `--rm` 容器内的 /tmp 会随容器销毁**——`docker run --rm ... tar
   -czf /tmp/x.tar.gz` 执行完文件就没了。必须 `-czf -` 输出到宿主机重定向
   (`... tar -C /v -czf - . > /tmp/x.tar.gz`)。
3. **Redis 只挂 conf 文件不带启动命令 = 无密码启动**——docker 镜像默认 CMD
   不读我们挂的路径,`requirepass` 形同虚设,`redis-cli` 报
   `AUTH ... called without any password configured`。必须显式
   `redis-server /usr/local/etc/redis/redis.conf`。
4. **组合长命令会被安全确认静默拦截**(超时 exit -1,像执行过实际没跑)——
   迁移类多步操作拆成单步小命令逐条执行。
5. 镜像传输用 `docker save -o x.tar` 后 `cat x.tar | ssh 'docker load'`;
   反向 `ssh 'docker save' | docker load` 不行(坑 1 的 stdout 污染)。

## 七、回滚

aliyun 侧所有容器/卷/数据**未删除**,仅应用容器 stop。回滚 =
起回容器 + nginx-proxy upstream 改回旧指向。长期不回滚则注意:aliyun 容器带
`restart=always`,宿主机重启会自启旧栈并开始写旧库。

## 附:博客库

同日博客(blog)库 36 表以同样 dump 方式导入 lv_zheda 同一 `blog_mysql`
实例(不同 schema),MinIO 数据按 `my-bucket/` 88 个文件核对。方法与本文档
完全一致,换 `01_export_db.sh blog` 即可。
