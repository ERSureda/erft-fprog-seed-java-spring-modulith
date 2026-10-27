---
name: generate-domain-models
description: >-
  Genera exclusivamente la definición del modelo (clases AggregateRoot y BaseEntity con
  atributos, constructores/factorías y getters, sin lógica de negocio) en Java 21 puro
  dentro de <base>/<module_id>/domain/model/ a partir de docs/modules/<module_id>/DOMAIN.md
  y la migración Flyway del módulo.
---

# Skill: Generación de Modelos de Dominio (`generate-domain-models`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/DOMAIN.md` y `V<N>__init_<module_id>_tables.sql` -> Extraer atributos, tipos de datos y nulabilidad de las tablas/entidades.
    3. Enums en `<base>.<module_id>.domain.model.enums.*` y Value Objects en `<base>.shared.domain.valueobject.*` (si aplican a los campos del modelo).
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `DOMAIN.md` o la migración `V<N>__init_<module_id>_tables.sql` no existen o conservan marcadores de plantilla (`[...]`, `<...>`).
        * Falta `package-info.java` (`generate-module-structure`), falta algún enum de estado referenciado (`generate-domain-enums`) o ya existe una clase de modelo cuyos atributos difieren de la especificación.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * Raíz del agregado: `src/main/java/<base>/<module_id>/domain/model/<AggregateRoot>.java` (extiende `AggregateRoot<UUID>`).
    * Entidad hija (si aplica): `src/main/java/<base>/<module_id>/domain/model/<SubordinateEntity>.java` (extiende `BaseEntity<UUID>`).
    * **PROHIBIDO** crear o modificar ningún otro archivo (ni eventos, ni excepciones, ni servicios, ni mappers, ni repositorios).
    * **PROHIBIDO** incluir métodos de mutación, transiciones de estado, predicados FSM ni lógica de negocio dentro del modelo.

## 2. REGLAS INMUTABLES (`DOM-01`, `DOM-02`, `DOM-04`, `TRX-02`, `SHR-04`, `SHR-05`)

1. **Pureza Absoluta (`DOM-01`)**: 100% Java 21 puro + `shared.domain.*`. **PROHIBIDO** importar Spring, Jakarta/JPA, Jackson o Lombok. Cero comentarios o bloques Javadoc, excepto los dos separadores de sección internos de la clase (`// --- Constructors & Factories ---` y `// --- Getters ---`). Imports explícitos ordenados alfabéticamente (`<base>.*` luego línea en blanco y `java.*`) sin comodines `*`.
2. **Estructura y Orden Canónico de la Clase**:
    * **Atributos en el tope de la clase**: `1. Referencias inmutables (final UUID)` -> `2. Atributos / Value Objects (Money)` -> `3. Estado (<Model>Status)` -> `4. Timestamps (final Instant createdAt; Instant updatedAt;)`.
    * **Exactamente 2 Secciones delimitadas por**:
        * `    // --- Constructors & Factories ---`
        * `    // --- Getters ---`
    * **PROHIBIDO incluir sección o métodos de lógica de negocio**: Cero métodos de transición FSM (`can<Action>()`), métodos mutadores (`<action>()`), o registro de eventos de dominio (`registerEvent(...)`).
3. **Constructores Privados y Factorías (`DOM-02`, `TRX-02`, `SHR-04`)**:
    * Constructor `private` que invoca `super(id, version);` en un `AggregateRoot<UUID>` o `super(id);` en una entidad subordinada (`BaseEntity<UUID>`) en la primera línea, y valida todo campo obligatorio con `Objects.requireNonNull(campo, "<ENTIDAD>_<CAMPO>_CANNOT_BE_NULL")` en el orden exacto de declaración. (Campos nullable en BD se asignan directamente sin `requireNonNull`).
    * `public static <Model> create(...)`: Inicializa `Instant now = Instant.now();`, instancia con estado inicial (si aplica), `version = 0L` (solo en `AggregateRoot<UUID>`; las entidades subordinadas `BaseEntity<UUID>` no tienen `version`), `createdAt = now`, `updatedAt = now` y retorna la instancia (sin registrar eventos de dominio ni ejecutar lógica adicional).
    * `public static <Model> reconstruct(...)`: Reconstruye desde persistencia asignando todos los campos directamente sin alterar timestamps (incluye `Long version` en `AggregateRoot<UUID>`; omite `Long version` en entidades subordinadas `BaseEntity<UUID>`).
4. **Getters y Cero Setters (`DOM-04`)**:
    * **PROHIBIDO** cualquier método `set*`.
    * Getters en el mismo orden exacto de declaración de atributos, separados por una línea en blanco, retornando `<field>` sin `this.`. **NO** redeclarar `getId()` ni `getVersion()` en `AggregateRoot<UUID>` (heredados), ni `getId()` en `BaseEntity<UUID>` (que solo hereda `getId()` y no tiene `getVersion()`).
5. **Exclusividad Estricta**:
    * El generador solo crea el modelo base de datos/entidad de dominio con sus atributos, factorías y getters. Nada más.

## 3. TABLA DE CONTRATOS CANÓNICOS (`shared.domain`)

| Contrato / Tipo Base | Firma / Fábrica Canónica | Regla de Uso en Modelo de Dominio |
| :--- | :--- | :--- |
| `AggregateRoot<ID>` | `protected AggregateRoot(ID id, Long version)` / `protected AggregateRoot(ID id)` | Clase base obligatoria para la raíz del agregado (hereda `getId()` y `getVersion()`). |
| `BaseEntity<ID>` | `protected BaseEntity(ID id)` | Clase base obligatoria para entidades subordinadas/hijas (solo hereda `getId()`, sin `version` ni `getVersion()`). |
| `Money` | `Money.of(BigDecimal, Currency\|String)`, `Money.zero(Currency)` | Value Object inmutable transversal para montos e importes monetarios. |

## 4. PLANTILLA CANÓNICA DE CÓDIGO

```java
package <base>.billing.domain.model;

import <base>.billing.domain.model.enums.InvoiceStatus;
import <base>.shared.domain.model.AggregateRoot;
import <base>.shared.domain.valueobject.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Invoice extends AggregateRoot<UUID> {

    private final UUID tenantId;
    private final UUID customerId;
    private final UUID tripId;
    private final String invoiceNumber;
    private final String recipientEmail;
    private Money totalAmount;
    private InvoiceStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    // --- Constructors & Factories ---
    private Invoice(
            UUID id,
            UUID tenantId,
            UUID customerId,
            UUID tripId,
            String invoiceNumber,
            String recipientEmail,
            Money totalAmount,
            InvoiceStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        super(id, version);
        this.tenantId = Objects.requireNonNull(tenantId, "INVOICE_TENANT_ID_CANNOT_BE_NULL");
        this.customerId = Objects.requireNonNull(customerId, "INVOICE_CUSTOMER_ID_CANNOT_BE_NULL");
        this.tripId = tripId;
        this.invoiceNumber = Objects.requireNonNull(invoiceNumber, "INVOICE_NUMBER_CANNOT_BE_NULL");
        this.recipientEmail = Objects.requireNonNull(recipientEmail, "INVOICE_EMAIL_CANNOT_BE_NULL");
        this.totalAmount = Objects.requireNonNull(totalAmount, "INVOICE_AMOUNT_CANNOT_BE_NULL");
        this.status = Objects.requireNonNull(status, "INVOICE_STATUS_CANNOT_BE_NULL");
        this.createdAt = Objects.requireNonNull(createdAt, "INVOICE_CREATED_AT_CANNOT_BE_NULL");
        this.updatedAt = Objects.requireNonNull(updatedAt, "INVOICE_UPDATED_AT_CANNOT_BE_NULL");
    }

    public static Invoice create(
            UUID id,
            UUID tenantId,
            UUID customerId,
            UUID tripId,
            String invoiceNumber,
            String recipientEmail,
            Money totalAmount
    ) {
        Instant now = Instant.now();
        return new Invoice(
                id,
                tenantId,
                customerId,
                tripId,
                invoiceNumber,
                recipientEmail,
                totalAmount,
                InvoiceStatus.DRAFT,
                0L,
                now,
                now
        );
    }

    public static Invoice reconstruct(
            UUID id,
            UUID tenantId,
            UUID customerId,
            UUID tripId,
            String invoiceNumber,
            String recipientEmail,
            Money totalAmount,
            InvoiceStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Invoice(
                id,
                tenantId,
                customerId,
                tripId,
                invoiceNumber,
                recipientEmail,
                totalAmount,
                status,
                version,
                createdAt,
                updatedAt
        );
    }

    // --- Getters ---
    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*DomainRulesArchTest*" --tests "*ModulithStructureTest*"`
3. Emitir informe final conciso:
    * **Generados**: `<AggregateRoot>` / `<SubordinateEntity>` con lista de atributos y firmas de `create(...)` y `reconstruct(...)`.
    * **Intactos (preexistentes)**.
    * **Recordatorio `DOM-03` / `DOM-05`**: Queda pendiente incorporar los predicados FSM (`can*()`), métodos de negocio y registro de `DomainEvent` en una fase posterior.
