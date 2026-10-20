# 生物信息学云平台 - Spring Boot 后端 Dockerfile
# 使用本地已构建的 JAR 包
#
# 注意：基底必须是公共基础镜像（eclipse-temurin），禁止用 bioplatform-backend 旧镜像做基底——
# 那会让每次 COPY 的新 jar 永久叠加一层（历史层不消失），镜像从 ~200MB 滚雪球到 2.4GB。
# 若构建时拉不到基础镜像，用 `docker pull eclipse-temurin:17-jre-alpine` 补缓存，
# 而不是退回 FROM 旧应用镜像的增量构建。

FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="bioplatform"
LABEL description="生物信息学云平台后端服务"

WORKDIR /app

# 复制本地已构建的 JAR 包
COPY target/*.jar app.jar

EXPOSE 8080

# 注意：不切换非 root 用户，因为 shell_execute 工具需要 chroot /host 执行宿主机命令

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
