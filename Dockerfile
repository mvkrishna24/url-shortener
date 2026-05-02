# ─── Stage 1: Build ──────────────────────────────────────────────────────────
# Use the official Maven image that bundles JDK 17.
# Separate the dependency-download step so Docker can cache that layer and
# avoid re-downloading the internet on every code change.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Download dependencies first (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build — skipping tests because integration tests need
# Docker-in-Docker (Testcontainers) which is unavailable during image build.
# Tests are run separately in CI before the image is built.
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
# JRE-only Alpine image: ~80 MB vs ~300 MB for a full JDK image.
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Never run as root inside a container.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the fat jar — not the entire Maven build output.
COPY --from=build /app/target/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# JVM tuning:
#   MaxRAMPercentage=75  → cap heap at 75% of cgroup memory limit (Render sets 512 MB)
#   UseG1GC              → predictable pause times for a latency-sensitive service
#   ExitOnOutOfMemoryError → crash-fast; let the platform restart rather than limp along
#   -Djava.security.egd   → faster SecureRandom init (avoids /dev/random blocking)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
