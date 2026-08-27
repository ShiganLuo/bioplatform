FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY bioplatform-springboot/target/*.jar app.jar
RUN mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar", "--spring.profiles.active=prod"]
