# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew shadowJar --no-daemon -x test

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S gitsage && adduser -S gitsage -G gitsage

WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar

RUN chown -R gitsage:gitsage /app
USER gitsage

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
