# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage  
FROM eclipse-temurin:21-jdk-alpine
RUN addgroup -g 1001 -S appgroup && adduser -S appuser -u 1001 -G appgroup
WORKDIR /app
COPY --from=builder /app/target/personal-ai-0.0.1-SNAPSHOT.jar app.jar
RUN ls -la /app/ && chmod 755 /app.jar  # Fix permissions + debug
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
