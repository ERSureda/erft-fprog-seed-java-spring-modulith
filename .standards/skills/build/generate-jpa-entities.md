---
name: generate-jpa-entities
description: >-
  Genera exclusivamente clases @Entity JPA en Java 21 dentro de
  infrastructure/adapter/out/persistence/postgres/jpa/entity/ a partir del script
  Flyway V<N>__init_<module_id>_tables.sql. Úsala tras definir el DDL del módulo.
---

# Skill: Generación de Entidades JPA (`generate-jpa-entities`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `src/main/resources/db/migration/V<N>__init_<module_id>_tables.sql` -> Extraer schema `<module_id>` y tablas de negocio.
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * No existe el script Flyway `V<N>__init_<module_id>_tables.sql` del módulo o carece de tablas de negocio.
        * Falta `package-info.java` (`generate-module-structure`) o ya existe una clase `<EntidadSingular>Entity` cuyos campos difieren del DDL.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/infrastructure/adapter/out/persistence/postgres/jpa/entity/<EntidadSingular>Entity.java`
    * **PROHIBIDO** generar `@Entity` para tablas técnicas (`audit_log*`, `outbox_events`, `processed_events`), alterar migraciones Flyway o modificar modelos de dominio. Si la entidad ya existe y coincide, dejarla intacta.

## 2. REGLAS INMUTABLES (`OUT-01`, `OUT-02`, `OUT-05`, `OUT-06`, `OUT-08`, `SHR-02`, `TRX-02`, `TRX-05`)

1. **Exclusión de Tablas Técnicas (`OUT-05`, `OUT-08`, `TRX-05`)**: **PROHIBIDO** generar `@Entity` para `audit_log*`, `outbox_events` y `processed_events` (se operan vía JDBC nativo). Mapear únicamente la tabla del Aggregate Root y sus tablas hijas de negocio.
2. **Confinamiento y Nomenclatura (`OUT-01`, `OUT-02`)**: Ubicación estricta en `..persistence.postgres.jpa.entity..`, sufijo `Entity` en `PascalCase`, `@Table(name = "<tabla>", schema = "<module_id>")`. Cero comentarios o bloques Javadoc. Imports explícitos sin `*`.
3. **Lombok Seguro en Entidades JPA (`OUT-01`)**: Anotar la clase exactamente con `@Getter`, `@Setter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` y `@AllArgsConstructor`. **PROHIBIDO** `@Data`, `@ToString` o `@EqualsAndHashCode`.
4. **Cero Acoplamiento Relacional (`OUT-06`)**: **PROHIBIDO** `@ManyToOne`, `@OneToOne`, `@OneToMany` o `@JoinColumn` hacia tablas de otros módulos (e incluso en tablas hijas intramodulares se prefiere FK escalar `UUID <padre>Id` salvo requerimiento explícito).
5. **Claves y Versionado (`SHR-02`, `TRX-02`)**:
    * PK: `@Id @Column(name = "id", nullable = false) private UUID id;` (**PROHIBIDO** `@GeneratedValue`).
    * Aggregate Root: incluye `@Version @Column(name = "version", nullable = false) private Long version;`. (Las tablas hijas subordinadas sin columna `version` en el DDL omiten `@Version`).

## 3. TABLA DE MAPEO DETERMINISTA (POSTGRESQL 17 -> JAVA 21)

| Columna PostgreSQL en DDL | Anotaciones Jakarta Persistence | Tipo Java |
| :--- | :--- | :--- |
| `id UUID PRIMARY KEY` | `@Id @Column(name = "id", nullable = false)` | `UUID` |
| `tenant_id UUID NOT NULL` | `@Column(name = "tenant_id", nullable = false)` | `UUID` |
| `<fk>_id UUID [NOT NULL]` | `@Column(name = "<fk>_id", nullable = ...)` | `UUID` |
| `version BIGINT NOT NULL` | `@Version @Column(name = "version", nullable = false)` | `Long` |
| `NUMERIC(p, s)` | `@Column(name = "...", nullable = ..., precision = p, scale = s)` | `BigDecimal` |
| `VARCHAR(n)` / Dominios texto | `@Column(name = "...", nullable = ..., length = n)` | `String` |
| `TEXT` / `JSONB` | `@Column(name = "...", columnDefinition = "TEXT" \| "jsonb")` | `String` |
| `ENUM` / `status VARCHAR(32)` | `@Column(name = "status", nullable = false, length = 32)` | `String` |
| `BOOLEAN` / `INTEGER` / `SMALLINT` | `@Column(name = "...", nullable = ...)` | `Boolean` / `Integer` / `Short` |
| `TIMESTAMPTZ` (`created_at`, etc.) | `@Column(name = "...", nullable = ...)` | `Instant` |

## 4. PLANTILLA CANÓNICA DE CÓDIGO

* **Orden Canónico de Atributos**: `1. id` -> `2. tenantId` -> `3. FKs escalares` -> `4. Campos de negocio` -> `5. version` -> `6. Timestamps (createdAt, updatedAt, deletedAt)`.

```java
package <base>.<module_id>.infrastructure.adapter.out.persistence.postgres.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices", schema = "billing")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InvoiceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*PersistenceRulesArchTest*" --tests "*ModulithStructureTest*"`
3. Emitir informe final conciso:
    * **Generadas**: `<module_id>.<tabla>` -> `<EntidadSingular>Entity` (indicando si incluye `@Version`).
    * **Omitidas (tablas técnicas)**: `audit_log*`, `outbox_events`, `processed_events`.
    * **Intactas (preexistentes)**.
