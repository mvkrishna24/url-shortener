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
# JRE-only Ubuntu image. Temurin's Java 17 Alpine tag does not publish an ARM64
# manifest, while Jammy supports both ARM64 development and AMD64 deployment.
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Never run as root inside a container.
RUN apt-get update \
    && apt-get install --no-install-recommends -y wget \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system appgroup \
    && useradd --system --gid appgroup --no-create-home appuser

# Copy only the fat jar and assign ownership in the same layer. A separate
# chown layer would duplicate the 78 MB jar in the final image.
COPY --chown=appuser:appgroup --from=build /app/target/*.jar app.jar

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
ENV SPRING_PROFILES_ACTIVE=prod

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
