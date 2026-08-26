# BioPlatform 无 Root 部署指南

适用场景：大学服务器、共享主机、无 sudo/docker 权限的 Linux 环境。

## 前提条件

服务器需要已有：
- MySQL 可用（本地或远程）
- Redis 可用（本地或远程，可选）

以下全部在用户目录下操作，不需要 root。

---

## 一、安装 JDK 17（用户目录）

```bash
mkdir -p ~/tools && cd ~/tools

# 下载 JDK 17（Eclipse Temurin）
wget https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.13%2B11/OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz
tar xzf OpenJDK17U-jdk_x64_linux_hotspot_17.0.13_11.tar.gz

# 加入 PATH
echo 'export PATH=~/tools/jdk-17.0.13+11/bin:$PATH' >> ~/.bashrc
echo 'export JAVA_HOME=~/tools/jdk-17.0.13+11' >> ~/.bashrc
source ~/.bashrc

# 验证
java -version
```

## 二、安装 Node.js 18（用户目录，仅构建时）

```bash
cd ~/tools
wget https://nodejs.org/dist/v18.20.4/node-v18.20.4-linux-x64.tar.xz
tar xJf node-v18.20.4-linux-x64.tar.xz

echo 'export PATH=~/tools/node-v18.20.4-linux-x64/bin:$PATH' >> ~/.bashrc
source ~/.bashrc

node -v && npm -v
```

## 三、数据库

如果服务器已有 MySQL：
```bash
# 创建数据库
mysql -h 数据库地址 -u 用户名 -p -e "CREATE DATABASE bioplatform DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h 数据库地址 -u 用户名 -p bioplatform < database/bioplatform.sql
```

如果没有 MySQL，用 Docker 启动（不需要 root，只需 Docker 权限）：
```bash
docker run -d --name bioplatform-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=bioplatform123 \
  -e MYSQL_DATABASE=bioplatform \
  -v ~/mysql-data:/var/lib/mysql \
  mysql:8.0.33
```

Redis 同理，如果没有可以用嵌入式方案或跳过（非核心功能）。

## 四、构建

```bash
cd /path/to/bioplatform

# 构建后端 JAR
cd bioplatform-springboot
mvn clean package -DskipTests -B
cd ..

# 构建前端
cd bioplatform-vue3/bioplatform-admin && npm install && npm run build && cd ../..
cd bioplatform-vue3/bioplatform-front && npm install && npm run build && cd ../..
```

## 五、部署目录

```bash
DEPLOY=~/bioplatform-deploy
mkdir -p $DEPLOY/{app,frontend/admin,frontend/front,uploads,logs}

cp bioplatform-springboot/target/bioplatform-*.jar $DEPLOY/app/bioplatform.jar
cp -r bioplatform-vue3/bioplatform-admin/dist/* $DEPLOY/frontend/admin/
cp -r bioplatform-vue3/bioplatform-front/dist/* $DEPLOY/frontend/front/
```

## 六、生产配置

```bash
cat > $DEPLOY/application-prod.yml << 'EOF'
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://数据库地址:3306/bioplatform?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: 数据库用户
    password: 数据库密码
  data:
    redis:
      host: redis地址
      port: 6379

app:
  upload:
    dir: /home/你的用户名/bioplatform-deploy/uploads
    ipPrefix: http://服务器IP:8080

jwt:
  secret: 换一个至少32字符的随机字符串
EOF
```

## 七、启动后端

```bash
# 直接运行
cd $DEPLOY
nohup java -Xms256m -Xmx512m \
  -Dspring.profiles.active=prod \
  -Dspring.config.additional-location=file:./application-prod.yml \
  -jar app/bioplatform.jar > logs/app.log 2>&1 &

echo $! > app.pid
echo "后端 PID: $(cat app.pid)"

# 查看日志
tail -f logs/app.log
```

停止后端：
```bash
kill $(cat app.pid)
```

## 八、前端用 Node.js serve（无 nginx）

```bash
npm install -g serve  # 如果没权限装全局，用 npx

# 后台管理（端口 8081）
nohup npx serve -s frontend/admin -l 8081 --no-clipboard > logs/admin.log 2>&1 &
echo $! > admin.pid

# 公开前台（端口 3000 或 80）
nohup npx serve -s frontend/front -l 3000 --no-clipboard > logs/front.log 2>&1 &
echo $! > front.pid
```

## 九、一键启动脚本

```bash
cat > $DEPLOY/start.sh << 'SCRIPT'
#!/bin/bash
DIR=$(cd "$(dirname "$0")" && pwd)
cd $DIR

# 启动后端
echo "启动后端..."
nohup java -Xms256m -Xmx512m \
  -Dspring.profiles.active=prod \
  -Dspring.config.additional-location=file:./application-prod.yml \
  -jar app/bioplatform.jar > logs/app.log 2>&1 &
echo $! > app.pid

# 等后端启动
echo "等待后端就绪..."
for i in $(seq 1 30); do
  curl -s http://localhost:8080/api/front/site-config > /dev/null 2>&1 && break
  sleep 2
done

# 启动前台
nohup npx serve -s frontend/front -l 3000 --no-clipboard > logs/front.log 2>&1 &
echo $! > front.pid

# 启动后台管理
nohup npx serve -s frontend/admin -l 8081 --no-clipboard > logs/admin.log 2>&1 &
echo $! > admin.pid

echo "启动完成！"
echo "  前台: http://$(hostname):3000"
echo "  后台: http://$(hostname):8081"
echo "  后端: http://$(hostname):8080"
SCRIPT

cat > $DEPLOY/stop.sh << 'SCRIPT'
#!/bin/bash
DIR=$(cd "$(dirname "$0")" && pwd)
cd $DIR
for f in app.pid front.pid admin.pid; do
  [ -f $f ] && kill $(cat $f) 2>/dev/null && rm $f && echo "停止 $(basename $f .pid)"
done
SCRIPT

chmod +x $DEPLOY/start.sh $DEPLOY/stop.sh
```

## 十、更新部署

```bash
cat > $DEPLOY/update.sh << 'SCRIPT'
#!/bin/bash
DIR=$(cd "$(dirname "$0")" && pwd)
SRC=/path/to/bioplatform  # 源码目录

cd $SRC && git pull origin main

cd $SRC/bioplatform-springboot && mvn clean package -DskipTests -B
cd $SRC/bioplatform-vue3/bioplatform-admin && npm run build
cd $SRC/bioplatform-vue3/bioplatform-front && npm run build

# 停止
$DIR/stop.sh

# 更新文件
cp $SRC/bioplatform-springboot/target/bioplatform-*.jar $DIR/app/bioplatform.jar
cp -r $SRC/bioplatform-vue3/bioplatform-admin/dist/* $DIR/frontend/admin/
cp -r $SRC/bioplatform-vue3/bioplatform-front/dist/* $DIR/frontend/front/

# 启动
$DIR/start.sh
SCRIPT

chmod +x $DEPLOY/update.sh
```

## 常见问题

**Q: 端口被占用怎么办？**
```bash
lsof -i:8080  # 查看谁在用
# 改 application-prod.yml 的 server.port 或 serve -l 的端口
```

**Q: 内存不够怎么办？**
```bash
# 降低 JVM 内存
java -Xms128m -Xmx256m ...
```

**Q: 没有域名只有 IP？**
直接用 IP 访问，前台 `http://IP:3000`，后台 `http://IP:8081`。

**Q: serve 命令不支持 WebSocket？**
serve 不支持 WebSocket 代理。如果需要在线客服功能，用 nginx 或 caddy：
```bash
# 下载 caddy（不需要 root）
wget https://github.com/caddyserver/caddy/releases/download/v2.8.4/caddy_2.8.4_linux_amd64.tar.gz
tar xzf caddy_2.8.4_linux_amd64.tar.gz
# 用 Caddyfile 反向代理（参考 Caddy 文档）
```
