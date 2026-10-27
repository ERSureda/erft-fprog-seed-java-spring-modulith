# ==============================================================================
# ETAPA 1: Compilación y empaquetado (Builder)
# ==============================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Copiar wrapper y configuración de dependencias para optimizar caché de capas
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copiar código fuente y generar el artefacto ejecutable
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ==============================================================================
# ETAPA 2: Runtime de producción seguro y ligero (Runner)
# ==============================================================================
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

# Creación de usuario sin privilegios administrativos (ADR_007: Principio de Mínimo Privilegio)
RUN addgroup -g 10001 -S nonroot && \
    adduser -u 10001 -S nonroot -G nonroot

# Copiar únicamente el artefacto compilado asignando propietario directamente (evita capas duplicadas)
COPY --chown=nonroot:nonroot --from=builder /workspace/build/libs/*.jar app.jar

USER nonroot:nonroot

EXPOSE 8080

# Sonda de salud nativa basada en Spring Boot Actuator
HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=20s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

# Flags de JVM optimizados para contenedores con ZGC generacional nativo (Java 25)
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"

# 'exec' asegura que Java tome PID 1 para recibir correctamente SIGTERM (Graceful Shutdown)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
