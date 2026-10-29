# Multi-stage build for Real-time Transcription API
# Stage 1: Build
FROM maven:3.9.5-eclipse-temurin-21 AS build

ARG BUILD_ENV=prod
ARG APP_VERSION=0.0.1-SNAPSHOT

WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Build arguments
ARG BUILD_ENV=prod
ARG APP_VERSION=0.0.1-SNAPSHOT

# Labels
LABEL maintainer="Bunny <support@rtt.app>"
LABEL version="${APP_VERSION}"
LABEL description="Real-time Transcription API"
LABEL environment="${BUILD_ENV}"

WORKDIR /app

# Install required packages: wget/curl for health checks, tzdata for IST timezone,
# setpriv + libcap so non-root user can bind privileged ports if needed
RUN apk add --no-cache wget curl tzdata setpriv libcap && \
    cp /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone && \
    apk del tzdata

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create directories for logs and temp files
RUN mkdir -p /app/logs /app/tmp && \
    chown -R appuser:appgroup /app

# Copy the built jar from build stage
COPY --from=build /app/target/rtt-api-*.jar app.jar
RUN chown appuser:appgroup app.jar

# Create version file
RUN echo "${APP_VERSION}" > /app/version.txt && \
    chown appuser:appgroup /app/version.txt

# Render sets PORT automatically; default to 8080
ENV PORT=8080

EXPOSE 8080

# Health check using the /healthz endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT}/healthz || exit 1

# JVM options for containerised environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heapdump.hprof \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8"

# Run as non-root appuser
ENTRYPOINT ["sh", "-c", "setpriv --reuid=appuser --regid=appgroup --clear-groups -- java $JAVA_OPTS -jar app.jar"]
