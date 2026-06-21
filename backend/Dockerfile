# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# Uses the full JDK so Maven can compile; the final image only needs the JRE.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy the Maven wrapper + descriptor first so the dependency-download layer
# is cached separately and only invalidated when pom.xml changes.
COPY .mvn/   .mvn/
COPY mvnw    pom.xml ./

RUN sed -i 's/\r$//' ./mvnw \
 && chmod +x ./mvnw \
 && ./mvnw dependency:resolve -DincludeScope=runtime -B --no-transfer-progress \
 && ./mvnw dependency:resolve-plugins -B --no-transfer-progress

# Now copy source and build the fat JAR (tests skipped — CI runs them separately).
COPY src/ src/
RUN ./mvnw package -DskipTests -B --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# Minimal JRE image; no build tools, no source code.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# wget for the HEALTHCHECK and basic troubleshooting.
RUN apk add --no-cache wget

# Non-root user — Railway and other platforms expect this.
RUN addgroup -S bulkemail \
 && adduser  -S bulkemail -G bulkemail

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

# Writable directories used by the app at runtime.
# On Railway these live in the ephemeral container filesystem (no persistence
# across deploys); use a mounted volume or cloud storage for durable data.
RUN mkdir -p /app/logs /app/exports /app/data \
 && chown -R bulkemail:bulkemail /app

USER bulkemail

# Railway injects PORT; Spring Boot reads ${PORT:8080}.
EXPOSE 8080

# Health check — used by docker-compose and local testing.
# Railway performs its own HTTP health check configured in the dashboard.
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Exec-form ENTRYPOINT so SIGTERM is delivered directly to the JVM, enabling
# Spring Boot's graceful shutdown instead of a forced process kill.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "/app/app.jar"]
