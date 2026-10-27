---
name: generate-application-results
description: >-
  Genera exclusivamente los DTOs inmutables de salida de aplicación (*Result) de un Bounded
  Context en Java 21 como records planos dentro de application/result/, a partir de los contratos
  de salida (OUT (Result) y proyecciones CQRS) de docs/modules/<module_id>/API.md.
---

# Skill: Generación de DTOs de Salida de Aplicación (`generate-application-results`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/API.md`: `Ruta Base REST` (o `Base Path REST`), tabla Sección 1 (`ID`, `Caso de Uso (*UseCase) y Comando (*Command)`, `Canal de Entrada (REST | ASYNC_WORKER | INTERNAL_PORT)`, `Tipo (ADR-006)`, `HTTP Status (@ResponseStatus)`), tabla Sección 2 (`ID`, `Caso de Uso (*UseCase) y Consulta (*Query)`, `Canal de Entrada (REST | INTERNAL_PORT)`, `Contenedor de Salida (*Result | PageResult | CursorResult)`, `HTTP Status`) y bloques de detalle `3.X` (viñetas `DTO de Salida Aplicación/Web` y `DTO de Salida Paginado`, filas con `Dirección: OUT (Result)` en `3.1`/`3.2` y tabla de proyecciones CQRS con columna `DTO Objetivo (*Result)` en `3.4`).
    3. Enums existentes en `<base>.<module_id>.domain.model.enums.*` (o en `shared`).
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `API.md` no existe, conserva marcadores de plantilla (`[...]`, `<...>`) o una operación HTTP con `HTTP Status != 204` (y distinta de `N/A (ACK)`) carece de especificación de salida en `3.X` (`OUT (Result)` o `DTO Objetivo (*Result)`).
        * Falta `package-info.java` (`generate-module-structure`) o falta algún enum de dominio referenciado por un campo de salida (`generate-domain-enums`).
        * Existen campos anidados complejos en la salida distintos de `items[].<campo>` (ej. `pickup.lat`) o un array sin estructura de paginación estándar (`CursorResult` / `PageResult`).
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/application/result/<Nombre>Result.java`
    * **PROHIBIDO** recrear envoltorios de paginación (`CursorResult` o `PageResult` ya existen en `shared.application.result`), añadir columnas de BD no listadas en la salida (`OUT (Result)` / `DTO Objetivo (*Result)`), incluir métodos/constructores/validaciones o exponer entidades/agregados/VOs (`APP-04`). Si un `*Result` ya existe y coincide, dejarlo intacto; si difiere, abortar informando.

## 2. REGLAS INMUTABLES (`APP-04`, `ARC-01`, `ARC-02`, `DOM-01`, `ADR_005`)

1. **Extracción de la "Forma" de Respuesta**:
    * Para cada DTO o bloque de operación con salida (`OUT (Result)` en `3.1`/`3.2` o filas agrupadas por `DTO Objetivo (*Result)` en `3.4`), su **forma** es la lista ordenada de pares `(nombreCampo, tipoJava)`:
        * Objeto simple: campos de salida declarados en `OUT (Result)` o bajo el mismo `DTO Objetivo (*Result)`.
        * Paginada (`CursorResult<<Item>Result>` / `PageResult<<Item>Result>`, o filas `items[].<campo>`): solo los campos del elemento interno `<Item>Result` (`items`, `nextCursor`, `hasNext`, `page`, `size`, `totalElements`, `totalPages` pertenecen al wrapper `CursorResult`/`PageResult` y quedan fuera del record).
        * `HTTP Status: 204` o `N/A (ACK)` sin `DTO de Salida Aplicación/Web` ni filas `OUT (Result)`: no genera `*Result` (retorno `void`).
2. **Determinación del Nombre del Record**:
    * **Regla Primaria (Nombre Explícito en `API.md`)**: Usar el nombre exacto del record `*Result` declarado explícitamente en `API.md` (en la viñeta `DTO de Salida Aplicación/Web` de `3.1`/`3.2`, en la columna `Contenedor de Salida (*Result | PageResult | CursorResult)` de la Sección 2 o en la columna `DTO Objetivo (*Result)` de `3.4`, ej. `<Entidad>Result`, `<Entidad>DetailResult`, `<Entidad>SummaryResult`, `<Entidad>StatusResult`). Si varias operaciones referencian el mismo nombre `<Nombre>Result` con idéntica estructura, se genera un único archivo `<Nombre>Result.java`.
    * **Regla de Fallback (Solo si `API.md` no especifica el nombre del DTO)**: Derivar el nombre por heurística de forma a partir de `Recurso` (primer segmento de la ruta tras `Ruta Base REST` en singular `PascalCase`, ej. `/orders` -> `Order`) y `<Operacion>` (nombre del caso de uso sin sufijo `UseCase`, `Command`, `Query` o `Port`).
3. **Mapeo de Tipos y Protección de Fronteras Spring Modulith (`ARC-01`, `ARC-02`, `ADR_005`)**:
    * `UUID` -> `UUID` | `String` -> `String` | `Instant` / `ISO-8601 (UTC)` -> `Instant` | `Int`/`Integer` -> `Integer` | `Long` -> `Long` | `Decimal`/`BigDecimal` -> `BigDecimal` | `Boolean` / `boolean` -> `Boolean` (o `boolean` si `API.md` declara el primitivo explícitamente).
    * `<AggregateName>Status` / `String (Enum)`:
        * Para operaciones de canal `REST` (consumidas por el controlador web local del mismo módulo), los campos de estado usan el **Enum puro de dominio** (`<base>.<module_id>.domain.model.enums.*` o `shared`) conforme a `ADR_005`. *(Esto garantiza tipado fuerte de extremo a extremo desde el `*RowMapper` JDBC hasta el controlador web).*
        * Si el `*Result` pertenece a una operación con `Canal de Entrada: INTERNAL_PORT` (contrato inter-módulo expuesto por `@NamedInterface("application")`), **PROHIBIDO** exponer en su firma un enum del paquete privado `<base>.<module_id>.domain.model.enums.*` (rompería `ModulithStructureTest` por `ARC-02` al ser consumido desde otro módulo); en `INTERNAL_PORT` solo se permite usar tipos estándar (`String`, `boolean`, etc., como en `QRY-03` de `API_Template.md` o `OrderResult.java` en `seed/`) o enums transversales de `shared`.
4. **Pureza Absoluta (`APP-04`, `DOM-01`, `INP-01`)**: Record Java 21 plano sin anotaciones de Jackson (`@JsonInclude`), Spring o Lombok, y sin métodos de mapeo estáticos (`from(...)`). Cero comentarios o bloques Javadoc. Imports explícitos sin `*`.

## 3. MATRIZ DETERMINISTA DE NOMENCLATURA Y FORMA (SALIDA API.MD -> RECORD JAVA 21)

| Prioridad | Condición en `API.md` | Nombre del Record a Generar | Regla de Tipos para Enums (`ARC-01`, `ARC-02`, `ADR_005`) |
| :---: | :--- | :--- | :--- |
| **1 (Primaria)** | Nombre explícito declarado en `DTO de Salida Aplicación/Web`, `Contenedor de Salida` o `DTO Objetivo (*Result)` | El nombre exacto declarado (ej. `<Entidad>Result`, `<Entidad>DetailResult`, `<Entidad>SummaryResult`, `<Entidad>StatusResult`) | Canal `REST`: Enum puro de dominio (`<base>.<module_id>.domain.model.enums.*` o `shared`). Canal `INTERNAL_PORT`: **PROHIBIDO** enum de `<base>.<module_id>.domain.model.enums.*`; usar `String` o enum de `shared`. |
| **2 (Fallback)** | Sin nombre explícito y **todas** las operaciones del recurso comparten exactamente la misma forma | `<Recurso>Result` (ej. `OrderResult`) | Ídem según `Canal de Entrada` (`REST` vs `INTERNAL_PORT`). |
| **3 (Fallback)** | Sin nombre explícito, el recurso tiene **varias formas distintas** y la operación devuelve un objeto único | `<Operacion>Result` (ej. `RegisterUserResult`) | Ídem según `Canal de Entrada` (`REST` vs `INTERNAL_PORT`). |
| **4 (Fallback)** | Sin nombre explícito, el recurso tiene **varias formas distintas** y la operación es paginada (`items[]`) | `<Operacion>ItemResult` (ej. `ListUsersByStatusItemResult`) | Ídem según `Canal de Entrada` (`REST` vs `INTERNAL_PORT`). |

## 4. PLANTILLAS CANÓNICAS DE CÓDIGO

### 4.1. Resultado de Operación (`RegisterUserResult.java`)

```java
package <base>.authentication.application.result;

import <base>.authentication.domain.model.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record RegisterUserResult(
        UUID id,
        UserStatus status,
        Instant createdAt
) {
}
```

### 4.2. Resultado de Elemento Paginado (`ListUsersByStatusItemResult.java`)

```java
package <base>.authentication.application.result;

import <base>.authentication.domain.model.enums.UserStatus;

import java.util.UUID;

public record ListUsersByStatusItemResult(
        UUID id,
        String email,
        UserStatus status
) {
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*ModulithStructureTest*" --tests "*ApplicationRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generados**: `<Nombre>Result` <- `ID` de operaciones que lo retornan (indicando si va envuelto en `CursorResult<...>` o `PageResult<...>`).
    * **Intactos** / **Sin resultado (`204` / `void`)** / **Fuera de alcance** / **Discrepancias de tipos o estructura**.
