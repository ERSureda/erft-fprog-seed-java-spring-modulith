# erft-fprog-seed-java-spring-modulith

Semilla base y preset oficial para el ecosistema **fprog**, diseñada para el desarrollo acelerado de servicios bajo arquitectura hexagonal, Domain-Driven Design (DDD) y modularidad estricta con **Spring Modulith**.

---

## 🚀 Stack Tecnológico

- **Java**: 25 (OpenJDK / Toolchain)
- **Framework**: Spring Boot 4.x & Spring Modulith
- **Base de Datos**: PostgreSQL 17 + Flyway Migrations
- **Patrones Clave**: Arquitectura Hexagonal (Puertos y Adaptadores), Outbox Pattern transaccional, Idempotencia JDBC, Aislamiento RLS (*Row Level Security*).
- **Calidad y Tests**: JUnit 5, Testcontainers, ArchUnit (validación de reglas arquitectónicas).
- **Documentación API**: OpenAPI 3 / Swagger UI (`springdoc`).

---

## 📁 Estructura Principal

```text
src/main/java/com/template/api/
├── shared/         # Kernel compartido inmutable (Context, Clock, Outbox, Idempotencia, RLS, Web)
└── ordering/       # Módulo funcional de referencia (Domain, Application, Infrastructure)

.standards/         # Reglas normativas sincronizadas del preset fprog (ADRs, Architecture, Testing)
```

---

## 📋 Requisitos Previos

- **JDK 25** instalado.
- **Docker** y **Docker Compose** en ejecución.

---

## ⚡ Inicio Rápido

### 1. Levantar la base de datos
```bash
docker compose up -d postgres
```

### 2. Ejecutar la aplicación
```bash
./gradlew bootRun
```
*(En Windows PowerShell: `.\gradlew.bat bootRun`)*

### 3. Verificar tests y reglas arquitectónicas
```bash
./gradlew check
```

---

## 🛠️ Comandos Útiles

| Comando | Descripción |
| :--- | :--- |
| `./gradlew bootRun` | Inicia el servicio localmente en `http://localhost:8080` |
| `./gradlew test` | Ejecuta la suite de pruebas unitarias y de integración |
| `./gradlew check` | Ejecuta verificaciones completas (tests + reglas ArchUnit/Modulith) |
| `./gradlew syncStandards` | Sincroniza y actualiza los estándares `.standards/` desde el repositorio central fprog |

---

## 🌐 Endpoints de Interés

- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
