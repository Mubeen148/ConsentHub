# syntax=docker/dockerfile:1
# ==============================================================================
# ConsentHub Backend — Multi-Stage Production Dockerfile
# Stage 1: Maven 3.9 + Eclipse Temurin JDK 21 Build
# Stage 2: Minimal Eclipse Temurin JRE 21 Non-Root Runtime
# ==============================================================================

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# Compile & package executable Spring Boot JAR using BuildKit Maven cache
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B --no-transfer-progress \
    && JAR_FILE=$(ls target/*.jar | grep -v '\-plain\.jar$' | head -n 1) \
    && cp "$JAR_FILE" /workspace/app.jar

# ------------------------------------------------------------------------------
# Runtime Stage — Minimal JRE 21 running as non-root `consenthub` user (SEC-G10)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk add --no-cache curl tzdata \
    && addgroup -S -g 10001 consenthub \
    && adduser -S -u 10001 -G consenthub -h /app -s /sbin/nologin consenthub

WORKDIR /app

COPY --from=build --chown=consenthub:consenthub /workspace/app.jar /app/app.jar

USER consenthub

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 \
    CMD curl -fsS http://localhost:8080/api/v1/health || curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/app/app.jar"]
