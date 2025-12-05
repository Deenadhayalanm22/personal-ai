# Minimal Dockerfile for Maven/Spring Boot
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/personal-ai-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

