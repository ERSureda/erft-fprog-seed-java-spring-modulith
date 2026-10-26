# ==============================================================================
# ETAPA 1: Compilación y empaquetado (Builder)
# ==============================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

# Copiar wrapper y configuración de dependencias para optimizar caché de capas
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

# Copiar código fuente y generar el artefacto ejecutable
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ==============================================================================
# ETAPA 2: Runtime de producción seguro y ligero (Runner)
# ==============================================================================
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

# Creación de usuario sin privilegios administrativos (Principio de Mínimo Privilegio)
RUN addgroup -g 10001 -S nonroot && \
    adduser -u 10001 -S nonroot -G nonroot

# Copiar únicamente el artefacto compilado desde la etapa anterior
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown -R nonroot:nonroot /app

USER nonroot:nonroot

EXPOSE 8080

# Flags de JVM optimizados para contenedores con ZGC generacional nativo (Java 25)
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
