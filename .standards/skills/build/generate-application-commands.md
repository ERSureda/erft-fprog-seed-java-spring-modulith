---
name: generate-application-commands
description: >-
  Genera exclusivamente los comandos inmutables de aplicación (*Command) de un Bounded Context
  en Java 21 dentro de application/command/ a partir de las operaciones de escritura de
  docs/modules/<module_id>/API.md, distinguiendo entre comandos WEB_EXCLUSIVE
  (records planos) y MULTICHANNEL (fail-fast con Objects.requireNonNull).
---

# Skill: Generación de Comandos de Aplicación (`generate-application-commands`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/API.md`: `Ruta Base REST` (o `Base Path REST`), tabla Sección 1 (`ID`, `Caso de Uso (*UseCase) y Comando (*Command)`, `Canal de Entrada (REST | ASYNC_WORKER | INTERNAL_PORT)`, `Tipo (ADR-006)`, `HTTP Status (@ResponseStatus)`) y bloques de detalle `3.X` (viñetas `DTO de Entrada Web`, `DTO de Mensaje Asíncrono`, `Parámetro de Ruta`, y tablas `Dirección (IN | OUT)` con filas `IN (Path)`, `IN (Request)`, `IN (Command)` y columna `Precondición en Constructor Compacto (ADR-006)` / `Token Estructurado ([ENTIDAD]_[CAMPO]_[REGLA])`). Ignorar `Cabeceras de Contexto Requeridas (ApiHeaders)`.
    3. Enums existentes en `<base>.<module_id>.domain.model.enums.*` (o en `shared`).
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `API.md` no existe, conserva marcadores de plantilla (`[...]`, `<...>`) o carece de bloque `3.X` (o especificación de campos de entrada `IN`) para algún comando en alcance.
        * Falta `package-info.java` (`generate-module-structure`), falta algún enum referenciado por un campo en comando `WEB_EXCLUSIVE` (`generate-domain-enums`), o el documento contiene campos anidados complejos (`stops[].address`, `pickup.latitude`) sin tipo auxiliar preexistente.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/application/command/<Operacion>Command.java`
    * **PROHIBIDO** generar comandos para consultas de la Sección 2 (`*Query`), crear tipos auxiliares anidados, o añadir datos del `ExecutionContext` (`tenantId`, `userId`, `roles` de `Cabeceras de Contexto Requeridas (ApiHeaders)`) que no figuren como parámetros de negocio en la ruta, cabecera de operación o filas `IN (Request)` / `IN (Command)`. Si el `*Command` ya existe y coincide, dejarlo intacto; si difiere, abortar informando.

## 2. REGLAS INMUTABLES (`APP-03`, `ADR_006`, `ADR_005`, `ARC-01`, `ARC-02`, `SHR-04`, `DOM-01`)

1. **Extracción y Clasificación por Canal (`APP-03`, `ADR_006`)**:
    * Extraer el nombre `<Operacion>Command` de la columna `Caso de Uso (*UseCase) y Comando (*Command)`. Clasificar según la columna `Tipo (ADR-006)` (o por `Canal de Entrada` si no se indica):
    * **`WEB_EXCLUSIVE`** (canal exclusivamente HTTP REST: `POST`, `PUT`, `PATCH`, `DELETE`): record Java 21 plano **sin** constructor compacto ni validaciones internas (delegadas a `@Valid` en `*HttpRequest`).
    * **`MULTICHANNEL`** (al menos un canal `ASYNC_WORKER`, `INTERNAL_PORT`, `EVT-xx` o scheduler): record Java 21 con **constructor compacto fail-fast** validando campos obligatorios mediante `Objects.requireNonNull(<campo>, "<ENTIDAD>_<CAMPO>_CANNOT_BE_NULL")` (o el token exacto declarado en `API.md` Sección `3.X`). `<ENTIDAD>` = Recurso en singular `UPPER_SNAKE_CASE` (`/orders` -> `ORDER`); si el nombre del campo ya empieza por la entidad (`orderId`), no duplicar el prefijo (`ORDER_ID_CANNOT_BE_NULL`). Omitir campos opcionales.
2. **Orden Estricto de Componentes**:
    1. **Parámetros de ruta (`Parámetro de Ruta` / `IN (Path)` / `{placeholder}`)**: por orden de aparición. Si es `{id}` inmediatamente tras `/<recurso>`, nombrarlo `<recursoSingular>Id` (`/orders/{id}` -> `orderId`, salvo que `API.md` especifique `id` en `toCommand(UUID id)` o en `IN (Path)`, respetando la firma de `API.md`); cualquier otro `{placeholder}` conserva su nombre literal. Tipo: `UUID`.
    2. **Cabeceras de operación (no pertenecientes a `ApiHeaders`)**: en el orden documentado, nombre en `camelCase` (`Idempotency-Key` -> `idempotencyKey`), tipo indicado (`UUID` o `String`). Ignorar siempre `X-Tenant-Id`, `X-User-Id`, `X-Roles`, `X-Correlation-Id`.
    3. **Campos de entrada (`IN (Request)` o `IN (Command)`)**: en el orden exacto documentado en el bloque `3.X`.
3. **Visibilidad de Enums en `application` (`ARC-01`, `ARC-02`, `ADR_005`)**:
    * En comandos `WEB_EXCLUSIVE` (`REST` local), usar directamente el **Enum puro de dominio** (`<base>.<module_id>.domain.model.enums.*` o `shared`) conforme a `ADR_005`.
    * En comandos `MULTICHANNEL` expuestos por `INTERNAL_PORT` (`SYNC-xx`) o consumidos desde `ASYNC_WORKER`, **PROHIBIDO** exponer enums del paquete privado `<base>.<module_id>.domain.model.enums.*` en la firma si cruzan fronteras modulares (usar `String` como en `ProcessOrderPaymentCommand(UUID orderId, String paymentStatus)` o enums transversales de `shared`).
4. **Pureza Absoluta (`APP-03`, `DOM-01`)**: **PROHIBIDO** importar Spring, Jakarta Validation (`@NotNull`, `@Email`), Jackson o Lombok. **Cero comentarios o bloques Javadoc.** Imports explícitos sin `*`.

## 3. TABLA DETERMINISTA DE CLASIFICACIÓN Y MAPEO DE TIPOS (API.MD -> COMMAND JAVA 21)

| Elemento / Tipo en `API.md` | Representación Java en `<Operacion>Command` |
| :--- | :--- |
| `Tipo (ADR-006): WEB_EXCLUSIVE` (`Canal: REST`) | `record` plano sin constructor compacto (validado previamente con `@Valid` en `*HttpRequest`). |
| `Tipo (ADR-006): MULTICHANNEL` (`ASYNC_WORKER`, `INTERNAL_PORT`) | `record` con constructor compacto y `Objects.requireNonNull(<campo>, "<TOKEN>")` en cada componente obligatorio. |
| `UUID` | `UUID` |
| `String` | `String` |
| `<AggregateName>Status` / `String (Enum)` | En `WEB_EXCLUSIVE` (`REST` local): Enum puro de dominio de `<base>.<module_id>.domain.model.enums.*` o `shared` (`ADR_005`). En `MULTICHANNEL` (`INTERNAL_PORT` / `ASYNC_WORKER`): `String` o enum transversal de `shared` (`ARC-02`). |
| `Instant` / `ISO-8601 (UTC)` | `Instant` |
| `Int` / `Integer` | `Integer` |
| `Long` | `Long` |
| `Decimal` / `BigDecimal` | `BigDecimal` |
| `Boolean` | `Boolean` |

## 4. PLANTILLAS CANÓNICAS DE CÓDIGO

### 4.1. Comando `WEB_EXCLUSIVE` (Record Plano)

```java
package <base>.authentication.application.command;

import java.util.UUID;

public record RegisterUserCommand(
        UUID idempotencyKey,
        String email,
        String phone,
        String displayName
) {
}
```

### 4.2. Comando `MULTICHANNEL` (Constructor Compacto Fail-Fast)

```java
package <base>.ordering.application.command;

import java.util.Objects;
import java.util.UUID;

public record ProcessOrderPaymentCommand(
        UUID orderId,
        String paymentStatus,
        String transactionRef
) {

    public ProcessOrderPaymentCommand {
        Objects.requireNonNull(orderId, "ORDER_ID_CANNOT_BE_NULL");
        Objects.requireNonNull(paymentStatus, "ORDER_PAYMENT_STATUS_CANNOT_BE_NULL");
    }
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*ModulithStructureTest*" --tests "*ApplicationRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generados**: `ID` -> `<Operacion>Command(componentes...)` (`WEB_EXCLUSIVE` o `MULTICHANNEL`).
    * **Intactos (preexistentes)** / **Fuera de alcance**.
    * **Recordatorio**: Los comandos `MULTICHANNEL` requieren prueba unitaria `<Operacion>CommandTest` verificando sus `Objects.requireNonNull`.
