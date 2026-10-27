---
name: generate-web-http-requests
description: >-
  Genera exclusivamente los DTOs de entrada HTTP (*HttpRequest) de un Bounded Context en Java 21
  como records inmutables con Bean Validation y método factoría toCommand(...) dentro de
  infrastructure/adapter/in/web/dto/, a partir de docs/modules/<module_id>/API.md
  y ensamblando los *Command ya existentes.
---

# Skill: Generación de DTOs de Petición HTTP (`generate-web-http-requests`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/API.md`: `Ruta Base REST` (o `Base Path REST`), tabla Sección 1 (`ID`, `Caso de Uso (*UseCase) y Comando (*Command)`, `Canal de Entrada (REST | ASYNC_WORKER | INTERNAL_PORT)`, `Tipo (ADR-006)`, `HTTP Status (@ResponseStatus)`) y bloques de detalle `3.X` (viñeta `DTO de Entrada Web`, `Parámetro de Ruta`, `Cabeceras de Contexto Requeridas (ApiHeaders)`, cabeceras de operación y filas con `Dirección: IN (Request)` / `IN (Path)`).
    3. `docs/modules/<module_id>/schema.sql` y `src/main/resources/db/migration/V1__init_shared_infrastructure.sql`: expresiones `CHECK` de dominios `shared.*` y tipos `ENUM`.
    4. Clases existentes en `<base>.<module_id>`: `application/command/<Operacion>Command.java` y enums en `domain/model/enums/*` (o `shared`).
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `API.md` no existe, tiene marcadores de plantilla (`[...]`, `<...>`) o carece de bloque `3.X` para alguna operación REST con cuerpo.
        * Falta `package-info.java` (`generate-module-structure`), falta el `<Operacion>Command` (`generate-application-commands`) o falta algún enum referenciado (`generate-domain-enums`).
        * Ningún constructor del `<Operacion>Command` puede cubrirse con los campos del record + variables de ruta + cabeceras sin pasar `null`.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/infrastructure/adapter/in/web/dto/<Operacion>HttpRequest.java`
    * **PROHIBIDO** generar `*HttpRequest` para consultas `GET`, operaciones `ASYNC_WORKER` / `INTERNAL_PORT`, o comandos sin filas `IN (Request)`. **PROHIBIDO** modificar `*Command`, crear controladores o tests. Si el `*HttpRequest` ya existe y coincide, dejarlo intacto; si difiere, abortar informando.

## 2. REGLAS INMUTABLES (`INP-01`, `APP-03`, `ADR_005`, `ADR_006`, `SHR-04`)

1. **Selección y Nomenclatura**: Un `record <Operacion>HttpRequest` (usando el nombre declarado en `DTO de Entrada Web`, o el nombre del `<Operacion>Command` reemplazando `Command` por `HttpRequest`) por cada comando de `Canal de Entrada` HTTP/REST (`POST`, `PUT`, `PATCH`, `DELETE`) con filas `IN (Request)` en su bloque `3.X`.
2. **Mapeo de Tipos (Usar siempre tipos envolventes para permitir `@NotNull`)**:
    * `UUID` -> `UUID` | `String` -> `String` | `<AggregateName>Status` / `String (Enum)` -> Enum de `domain.model.enums.*` (`ADR_005`) | `Instant` / `ISO-8601 (UTC)` -> `Instant` | `Int`/`Integer` -> `Integer` | `Long` -> `Long` | `Decimal`/`BigDecimal` -> `BigDecimal` | `Boolean` -> `Boolean`.
    * *Campos anidados (`stops[].address`, `pickup.lat`)*: el componente de primer nivel (`stops`, `pickup`) toma exactamente el tipo del componente homónimo en `<Operacion>Command`. (Las sub-reglas anidadas se reportan en el informe final).
3. **Gramática de Tokens de Validación (`SHR-04`)**:
    * Usar el token exacto declarado en la columna `Token de Validación ([ENTIDAD]_[CAMPO]_[REGLA]) / Descripción` de `3.X`; si no se especifica explícitamente para una regla, derivarlo con formato `<ENTIDAD>_<CAMPO>_<REGLA>` en `UPPER_SNAKE_CASE`.
    * `<ENTIDAD>` = Primer segmento de la ruta tras `Ruta Base REST` en singular mayúsculas (`/users` -> `USER`, `/orders` -> `ORDER`).
    * `<CAMPO>` = Nombre del componente en `UPPER_SNAKE_CASE` (`displayName` -> `DISPLAY_NAME`).
4. **Construcción de la Factoría `toCommand(...)`**:
    * **Parámetros de `toCommand`**: Respetar la firma declarada en `DTO de Entrada Web` (ej. `toCommand()` o `toCommand(UUID id)`), incluyendo solo los valores externos al body (`IN (Path)` / `Parámetro de Ruta` y cabeceras de operación distintas de `ApiHeaders`) que el constructor de `<Operacion>Command` requiera, en este orden: `1. Variables de ruta ({placeholder} / Parámetro de Ruta)` -> `2. Cabeceras de operación` (`camelCase`, `UUID` o `String`). Si no requiere externos, `toCommand()` va sin argumentos.
    * **Enlace con `<Operacion>Command`**: Elegir el constructor existente cuyos parámetros se cubran al 100% (primero por nombre con `this.<campo>` y parámetros de `toCommand`, y el resto por orden con variables de ruta del mismo tipo). Ignorar `tenantId`/`userId` si el comando ofrece constructor sin ellos. **PROHIBIDO** pasar `null`.
5. **Pureza de Formato (`INP-01`)**: Cero comentarios o bloques Javadoc. Imports explícitos sin `*`.

## 3. TABLA CERRADA DE ANOTACIONES BEAN VALIDATION (JAKARTA VALIDATION)

Orden de evaluación por campo: `1. Obligatoriedad` -> `2. Longitud` -> `3. Formato` -> `4. Rango`. *(Si la columna `Token de Validación` declara un token específico para esa restricción, usar dicho token en `message`).*

| Regla en `API.md` (`Nulabilidad / Validación`) | Anotación Jakarta Validation |
| :--- | :--- |
| `@NotBlank` / `Requerido: SÍ` en `String` | `@NotBlank(message = "<ENTIDAD>_<CAMPO>_REQUIRED")` |
| `@NotNull` / `Requerido: SÍ` en otro tipo | `@NotNull(message = "<ENTIDAD>_<CAMPO>_REQUIRED")` |
| `@Size(max = N)` / `máx. N caracteres` | `@Size(max = N, message = "<ENTIDAD>_<CAMPO>_MAX_LENGTH")` (o `..._INVALID_LENGTH` según token en `API.md`) |
| `@Size(min = a, max = b)` / `longitud a..b caracteres` | `@Size(min = a, max = b, message = "<ENTIDAD>_<CAMPO>_INVALID_LENGTH")` |
| `NotBlank si presente` (en opcional) | `@Pattern(regexp = ".*\\S.*", message = "<ENTIDAD>_<CAMPO>_BLANK")` |
| `Formato <dominio>` (`shared.*`) / `@Pattern` | `@Pattern(regexp = "<REGEX>", message = "<ENTIDAD>_<CAMPO>_INVALID_FORMAT")` copiando el `CHECK` de `schema.sql` / `V1__init_shared_infrastructure.sql` (Defaults estándar: `shared.email` = `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$`; `shared.phone_e164` = `^\\+[1-9]\\d{1,14}$`; `shared.kebab_code` = `^[a-z0-9]+(-[a-z0-9]+)*$`; `shared.currency` = `^[A-Z]{3}$`; `shared.country_code` = `^[A-Z]{2}$`). |
| `@Positive` / `@Min` / `@Max` / `@DecimalMin` / `@DecimalMax` | `@Positive(message = "<ENTIDAD>_<CAMPO>_MUST_BE_POSITIVE")`, `@Min(value = X, message = "..._MIN_VALUE")`, `@Max(value = X, message = "..._MAX_VALUE")`, `@DecimalMin(value = "X", message = "..._MIN_VALUE")`, `@DecimalMax(value = "X", message = "..._MAX_VALUE")`. |
| `<AggregateName>Status` / `String (Enum)` | Sin anotación de formato (el propio tipo Enum valida el catálogo). |

## 4. PLANTILLA CANÓNICA DE CÓDIGO

```java
package <base>.authentication.infrastructure.adapter.in.web.dto;

import <base>.authentication.application.command.RegisterUserCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterUserHttpRequest(
        @NotBlank(message = "USER_EMAIL_REQUIRED")
        @Size(max = 255, message = "USER_EMAIL_INVALID_LENGTH")
        @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "USER_EMAIL_INVALID_FORMAT")
        String email,

        @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "USER_PHONE_INVALID_FORMAT")
        String phone,

        @Pattern(regexp = ".*\\S.*", message = "USER_DISPLAY_NAME_BLANK")
        @Size(min = 1, max = 160, message = "USER_DISPLAY_NAME_INVALID_LENGTH")
        String displayName
) {

    public RegisterUserCommand toCommand(UUID idempotencyKey) {
        return new RegisterUserCommand(idempotencyKey, this.email, this.phone, this.displayName);
    }
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*WebRulesArchTest*" --tests "*ModulithStructureTest*"`
3. Emitir informe final conciso:
    * **Generados**: `ID` -> `<Operacion>HttpRequest` y firma de `toCommand(...)`.
    * **Intactos** / **Omitidos (sin body)** / **Reglas delegadas al dominio** / **Discrepancias**.
    * **Recordatorio `ADR_006`**: Queda pendiente generar `<Operacion>HttpRequestTest` para probar `toCommand(...)`.
