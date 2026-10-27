---
name: generate-application-queries
description: >-
  Genera exclusivamente los modelos de consulta de aplicación (*Query) de un Bounded Context
  en Java 21 como records planos inmutables dentro de application/query/, a partir de las
  operaciones de lectura con parámetros en docs/modules/<module_id>/API.md.
---

# Skill: Generación de Consultas de Aplicación (`generate-application-queries`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/API.md`: `Ruta Base REST` (o `Base Path REST`), tabla Sección 2 (`ID`, `Caso de Uso (*UseCase) y Consulta (*Query)`, `Canal de Entrada (REST | INTERNAL_PORT)`, `Contenedor de Salida (*Result | PageResult | CursorResult)`, `HTTP Status`) y bloques de detalle `3.X` / `3.4` (`Parámetros QRY-xx`, `Parámetro de Ruta`, cabeceras de operación y tabla de proyecciones con `DTO Objetivo (*Result)`).
    3. Enums existentes en `<base>.<module_id>.domain.model.enums.*` (o en `shared`).
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `API.md` no existe, conserva marcadores de plantilla (`[...]`, `<...>`) o carece de bloque `3.X` / viñeta `Parámetros QRY-xx` para alguna query con parámetros en alcance.
        * Falta `package-info.java` (`generate-module-structure`) o falta algún enum de dominio referenciado en los parámetros (`generate-domain-enums`).
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/application/query/<Operacion>Query.java` (tomando el nombre `<Operacion>Query` declarado en la columna `Caso de Uso (*UseCase) y Consulta (*Query)`).
    * **PROHIBIDO** generar `*Query` para filas que solo declaren un puerto `*Port` sin `*Query` (ej. `VerifyActiveUserPort`) o para consultas sin ningún parámetro (esas usan `execute()` sin argumentos). **PROHIBIDO** tocar otras clases o añadir campos de `ExecutionContext` (`tenantId`, `userId`, `roles` de `Cabeceras de Contexto Requeridas (ApiHeaders)`) no declarados como parámetros de la consulta. Si el `*Query` ya existe y coincide, dejarlo intacto; si difiere, abortar informando.

## 2. REGLAS INMUTABLES (`APP-03`, `OUT-04`, `ARC-01`, `ARC-02`, `DOM-01`, `ADR_005`)

1. **Pureza de Record Plano (`APP-03`, `DOM-01`)**: Record Java 21 plano inmutable. **PROHIBIDO** incluir constructores compactos, validaciones (`Objects.requireNonNull`, `@Max`, `@NotNull`), métodos, valores por defecto o dependencias de Spring/Jakarta/Jackson/Lombok. (Los defaults y límites `@Max`/`@Min` los aplica el controlador web). Cero comentarios o bloques Javadoc. Imports explícitos sin `*`.
2. **Orden Estricto de Componentes**:
    * Si la viñeta `Parámetros QRY-xx (<Operacion>Query)` en `3.4` declara explícitamente la lista de parámetros y sus tipos, respetar su orden, nombres y tipos declarados; en caso de derivarlos de la ruta/cabeceras/query params de `3.X`, aplicar este orden:
    1. **Variables de ruta (`{placeholder}` / `Parámetro de Ruta` / `IN (Path)`)**: por orden de aparición en la ruta. Si es `{id}` inmediatamente tras `/<recurso>` y no tiene otro nombre explícito en `Parámetros QRY-xx`, nombrarlo `<recursoSingular>Id` (`/orders/{id}` -> `orderId`) o `id` según lo declare `Parámetros QRY-xx`; cualquier otro `{placeholder}` conserva su nombre literal. Tipo: `UUID`.
    2. **Cabeceras de negocio** (excluyendo `Cabeceras de Contexto Requeridas (ApiHeaders)` como `X-Tenant-Id`, `X-User-Id`, `X-Roles`): en el orden documentado, nombre en `camelCase`, tipo indicado (`UUID` o `String`).
    3. **Parámetros de consulta (`Parámetros QRY-xx` / `Query Params`)**: en el orden documentado con su nombre literal (incluyendo `cursor` y `limit` o `page` y `size`).
3. **Protección de Fronteras Spring Modulith para Enums (`ARC-01`, `ARC-02`, `ADR_005`)**:
    * Para operaciones con `Canal de Entrada: REST` (consumidas por el controlador web local del mismo módulo), los parámetros de estado o clasificación usan el **Enum puro de dominio** (`<base>.<module_id>.domain.model.enums.*` o `shared`) conforme a `ADR_005`.
    * Si el `*Query` pertenece a una operación con `Canal de Entrada: INTERNAL_PORT` (contrato inter-módulo expuesto por `@NamedInterface("application")`), **PROHIBIDO** exponer en su firma un enum del paquete privado `<base>.<module_id>.domain.model.enums.*` (rompería `ModulithStructureTest` por `ARC-02` al ser consumido desde otro módulo); en `INTERNAL_PORT` solo se permite usar tipos estándar (`String`, `boolean`, etc., como en `QRY-03` de `API_Template.md` o `OrderResult.java` en `seed/`) o enums transversales de `shared`.

## 3. TABLA DE MAPEO DETERMINISTA DE TIPOS (API.MD -> RECORD JAVA 21)

| Tipo en `API.md` | Tipo Java en `<Operacion>Query` |
| :--- | :--- |
| `UUID` | `UUID` |
| `String` | `String` |
| `<AggregateName>Status` / `String (Enum)` | En canal `REST`: Enum puro de dominio de `<base>.<module_id>.domain.model.enums.*` o `shared` (`ADR_005`). En canal `INTERNAL_PORT`: **PROHIBIDO** exponer enums de `<base>.<module_id>.domain.model.enums.*` (`ARC-02`); usar `String` o enum transversal de `shared`. |
| `Instant` / `ISO-8601 (UTC)` | `Instant` |
| `int` / `Int` / `Integer` **con** `por defecto` / `default` | `int` (primitivo: el controlador garantiza valor por defecto) |
| `Integer` / `Int` **sin** `por defecto` / `default` | `Integer` (envolvente: puede ser `null` si es opcional) |
| `long` / `Long` / `boolean` / `Boolean` | `long` / `boolean` si tiene valor por defecto; `Long` / `Boolean` si es opcional sin valor por defecto. |
| `BigDecimal` / `Decimal` | `BigDecimal` |

## 4. PLANTILLAS CANÓNICAS DE CÓDIGO

### 4.1. Consulta puntual por identificador

```java
package <base>.ordering.application.query;

import java.util.UUID;

public record GetOrderByIdQuery(
        UUID orderId
) {
}
```

### 4.2. Listado filtrado y paginado por cursor

```java
package <base>.authentication.application.query;

import <base>.authentication.domain.model.enums.UserStatus;

public record ListUsersByStatusQuery(
        UserStatus status,
        String cursor,
        int limit
) {
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*ModulithStructureTest*" --tests "*ApplicationRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generadas**: `ID` -> `<Operacion>Query(componentes...)`.
    * **Intactas** / **Sin parámetros (`execute()`)** / **Omitidas (`*Port`)** / **Discrepancias de tipos o parámetros**.
