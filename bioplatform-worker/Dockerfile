# BioPlatform Worker 计算节点镜像
# bash: 任务命令以 bash -c 执行;docker-cli: 任务内的 docker run 经宿主机 docker.sock 执行(DooD)
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash docker-cli

WORKDIR /app
COPY target/bioplatform-worker.jar app.jar

# /data/tasks 挂卷:任务状态持久化(平台重启对账依据)
VOLUME /data/tasks

EXPOSE 18081
ENTRYPOINT ["java", "-jar", "app.jar"]
