# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:23-jdk-alpine AS builder
WORKDIR /build

COPY . .
RUN ./gradlew buildFatJar --no-daemon

FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

RUN apk add --no-cache wget

COPY --from=builder /build/build/libs/*-all.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]