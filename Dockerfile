FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/claude-ai-spring-boot-1.0.0.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
