# 生物信息学云平台 - Spring Boot 后端 Dockerfile
# 使用本地已构建的 JAR 包

FROM eclipse-temurin:17-jre

LABEL maintainer="bioplatform"
LABEL description="生物信息学云平台后端服务"

WORKDIR /app

# 复制本地已构建的 JAR 包
COPY target/*.jar app.jar

EXPOSE 8080

# 注意：不切换非 root 用户，因为 shell_execute 工具需要 chroot /host 执行宿主机命令

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
