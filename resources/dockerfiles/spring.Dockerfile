# ---------- Build Stage ----------
FROM gradle:jdk25 AS builder
WORKDIR /app
# Copy Gradle files first for better layer caching
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./
# Download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon || true
# Copy source code
COPY src src
COPY config config
# Build application (skip tests)
RUN ./gradlew  build -x test --no-daemon
# ---------- Runtime Stage ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /app/images # for storing image
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]