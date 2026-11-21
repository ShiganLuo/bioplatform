# BioPlatform Worker 计算节点镜像
# bash: 任务命令以 bash -c 执行;docker-cli: 任务内的 docker run 经宿主机 docker.sock 执行(DooD)
# 17.0.21.1_2 = CVE-2026-114938 (Java4Shell) 修复版,勿退回浮动 tag 17-jre-alpine
FROM eclipse-temurin:17.0.21.1_2-jre-alpine

RUN apk add --no-cache bash docker-cli

WORKDIR /app
COPY target/bioplatform-worker.jar app.jar

# /data/tasks 挂卷:任务状态持久化(平台重启对账依据)
VOLUME /data/tasks

EXPOSE 18081
ENTRYPOINT ["java", "-jar", "app.jar"]
