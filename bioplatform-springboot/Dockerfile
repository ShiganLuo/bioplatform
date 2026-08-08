# ============================================================
# 生物信息学云平台 - Spring Boot 后端 Dockerfile
# 多阶段构建（Multi-stage Build）
# 阶段1：使用 Maven 构建 JAR 包
# 阶段2：使用轻量级 JRE 运行应用
# ============================================================

# ==================== 阶段1：构建阶段 ====================
# 使用 Maven 3.9.9 + Eclipse Temurin JDK 17 构建项目
FROM maven:3.9.9-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /app

# 第一步：仅复制 pom.xml，利用 Docker 层缓存加速依赖下载
# 只要 pom.xml 没有变化，这一层就会被缓存，不会重新下载依赖
COPY pom.xml .

# 下载所有 Maven 依赖（利用 Docker 缓存机制）
# -B: 批处理模式（减少日志输出） -U: 强制更新快照
RUN mvn dependency:go-offline -B

# 第二步：复制源代码并编译打包
COPY src ./src

# 执行 Maven 构建，跳过测试（测试在 CI/CD 中单独执行）
# -DskipTests: 跳过测试  -B: 批处理模式
RUN mvn package -DskipTests -B

# ==================== 阶段2：运行阶段 ====================
# 使用轻量级 Eclipse Temurin JRE 17 镜像（不包含 JDK，体积更小）
FROM eclipse-temurin:17-jre

# 维护者信息
LABEL maintainer="bioplatform"
LABEL description="生物信息学云平台后端服务"

# 设置工作目录
WORKDIR /app

# 从构建阶段复制编译好的 JAR 包
# 通配符匹配 Spring Boot 打包的 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 暴露后端服务端口
EXPOSE 8080

# 创建非 root 用户运行应用（安全最佳实践）
RUN groupadd -r bioplatform && useradd -r -g bioplatform bioplatform

# 设置文件权限
RUN chown -R bioplatform:bioplatform /app

# 切换到非 root 用户
USER bioplatform

# 容器启动命令
# JAVA_OPTS: 通过环境变量传入 JVM 参数（在 docker-compose.yml 中配置）
# 使容器能够优雅关闭（接收 SIGTERM 信号）
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
