FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY core ./core
COPY modules ./modules
COPY application ./application
RUN mvn --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
RUN addgroup -g 1001 -S appgroup && adduser -S appuser -u 1001 -G appgroup
WORKDIR /app
COPY --from=builder /app/application/target/personal-ai-application-0.0.1-SNAPSHOT.jar app.jar
RUN ls -la /app/ && chmod 755 /app/app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
