---
name: generate-db-migration
description: >-
  Genera exclusivamente scripts DDL de migración Flyway inmutables para PostgreSQL 17
  (V<N>__init_<module_id>_tables.sql o evolutivas V<N>__<verbo>_<desc>.sql) formateados
  a 80 columnas con aislamiento de esquema, tipos ENUM nativos, UUIDv7, concurrencia optimista,
  fillfactor = 90, políticas RLS y cero claves foráneas intermodulares.
---

# Skill: Generación de Migraciones Flyway PostgreSQL 17 (`generate-db-migration`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/modules/<module_id>/schema.sql` (o especificación DDL de entrada).
    2. `src/main/resources/db/migration/` -> Calcular el siguiente entero secuencial `V<N>` sin colisiones.
    3. `src/main/resources/db/migration/V1__init_shared_infrastructure.sql` -> Extraer el nombre exacto de `<app_role>` (ej. `template_app`), tipos enumerados globales y dominios `shared.*`.
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `schema.sql` no existe, conserva marcadores de plantilla (`[...]`, `<...>`) o incluye tablas sin un módulo (`<module_id>`) claro.
        * No existe `V1__init_shared_infrastructure.sql` o ya existe una migración para el módulo que difiere del DDL especificado.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * Baseline modular: `src/main/resources/db/migration/V<N>__init_<module_id>_tables.sql` (con doble guion bajo `__`).
    * Evolución incremental: `src/main/resources/db/migration/V<N>__<add|alter|create_idx|drop>_<descripcion>.sql`.
    * **PROHIBIDO** modificar migraciones previas ya existentes, tocar `V1`, alterar archivos YAML o generar código Java.

## 2. REGLAS INMUTABLES (`OUT-06`, `OUT-07`, `OUT-08`, `TRX-02`, `SHR-02`, `SHR-03`)

1. **Esquema Propio y Cero FKs Cruzadas (`OUT-06`)**: Un archivo por Bounded Context. Iniciar siempre con `CREATE SCHEMA IF NOT EXISTS <module_id>;` y `SET search_path TO <module_id>, shared, public;`. Calificar siempre `<module_id>.<tabla>`. **PROHIBIDO** declarar `FOREIGN KEY` / `REFERENCES` hacia otros esquemas/módulos (mapear como `UUID` escalar + índice B-Tree `idx_<tabla>_<otro_mod>`). Las FKs intramodulares sí llevan `CONSTRAINT fk_<hija>_<padre> FOREIGN KEY (...) REFERENCES <module_id>.<padre> (id) ON DELETE CASCADE` + índice B-Tree.
2. **Orden Canónico de Columnas en Tablas (`OUT-07`, `TRX-02`, `SHR-02`)**:
    1. **Control de Concurrencia y Temporalidad (en cabecera)**: `version BIGINT NOT NULL DEFAULT 0` (obligatorio en todo Aggregate Root), `created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`, `deleted_at TIMESTAMPTZ` (si aplica soft-delete). **PROHIBIDO** `TIMESTAMP` sin zona horaria.
    2. **Estados y Tipología**: `status <module_id>.<entity>_status NOT NULL DEFAULT '...'` (tipado con el `ENUM` nativo declarado en la Sección 1 del script) y flags booleanos de estado con `DEFAULT`. **PROHIBIDO** añadir constraints redundantes `CHECK (status IN (...))`, ya que el propio motor PostgreSQL valida el catálogo mediante el tipo `ENUM`.
    3. **Identidad y Aislamiento Multi-Cliente**: `id UUID NOT NULL` (**PROHIBIDO** `SERIAL`/`BIGSERIAL`/`gen_random_uuid()`), `tenant_id UUID NOT NULL` (en toda tabla multi-tenant, o `UUID` si es opcional/pública) y, en entidades subordinadas, `<agg>_id UUID NOT NULL` (FK intramodular).
    4. **Atributos y Referencias de Negocio**: referencias escalares intermodulares (`<ext_mod>_id UUID`), dominios `shared.*` (`shared.currency`, `shared.email`, `shared.phone_e164`, `shared.kebab_code`, `shared.country_code`, `shared.language`, `shared.sha256_hex`), `NUMERIC(p, s)` para importes/coordenadas (**PROHIBIDO** `FLOAT`/`DOUBLE`/`REAL`), `VARCHAR`, `INTEGER`, etc.
3. **Constraints Prefijadas y Almacenamiento (`OUT-07`)**:
    * Prefijos obligatorios: `pk_`, `fk_`, `uq_`, `chk_`.
    * Checks obligatorios canónicos:
      - `CONSTRAINT chk_<tabla>_version_non_neg CHECK (version >= 0)`
      - `CONSTRAINT chk_<tabla>_updated_coherence CHECK (updated_at >= created_at)`
      - `CONSTRAINT chk_<tabla>_deleted_coherence CHECK (deleted_at IS NULL OR deleted_at >= created_at)`
    * Tabulación y alineación visual limpia en columnas para campos y constraints.
    * Cerrar toda tabla de negocio con `) WITH (fillfactor = 90);` (o `fillfactor = 85` en entidades subordinadas de alta rotación).
4. **Sistema de Comentarios Estructurales en 3 Niveles a 80 Columnas (`OUT-07`)**:
    * **Nivel 1**: Banner superior de 80 caracteres exactos (`-- =============================================================================`).
    * **Nivel 2**: Divisores de sección numerados de 80 caracteres exactos (`-- -----------------------------------------------------------------------------`).
    * **Nivel 3**: Sub-bloques (`-- Índices operativos y de unicidad`, `-- Índices de entidad subordinada`).
    * **Sentencias DDL `COMMENT ON`**: **PROHIBIDAS** / Omitidas (no declarar `COMMENT ON TABLE` ni `COMMENT ON COLUMN` para mantener los scripts DDL limpios, evitar fallos de codificación de caracteres y simplificar la mantenibilidad).
5. **Centralización de Infraestructura en Monolito (`OUT-08`, `TRX-03`, `TRX-05`, `SHR-03`)**:
    * En fase de **monolito modular con Spring Modulith**, las tablas técnicas `shared.outbox_events`, `shared.processed_events` y la bitácora forense `shared.audit_log` residen de forma compartida y centralizada en el esquema `shared` (inicializadas en `V1__init_shared_infrastructure.sql`). Los módulos de negocio **no** deben crear tablas locales de outbox ni audit_log.
    * Cuando un Bounded Context se separe a futuro en un **microservicio autónomo** con su propia base de datos, en ese momento aprovisionará sus versiones locales (`<module_id>.outbox_events`, `<module_id>.processed_events`, `<module_id>.audit_log`).
    * Activar `ENABLE ROW LEVEL SECURITY` y `FORCE ROW LEVEL SECURITY` en todas las tablas con `tenant_id`.
    * Envolver todos los `GRANT`/`REVOKE` del rol de aplicación (`<app_role>`, ej. `template_app` extraído de `V1__init_shared_infrastructure.sql`) dentro de un bloque seguro `DO $$ BEGIN IF EXISTS (SELECT FROM pg_roles WHERE rolname = '<app_role>') THEN ... END IF; END $$;` para evitar fallos en Testcontainers cuando los roles de producción no existen.

## 3. MATRIZ DE TIPOS, DOMINIOS Y CONSTRAINTS CANÓNICAS (POSTGRESQL 17)

| Concepto / Uso | Tipo / Dominio DDL | Regla / Restricción Obligatoria |
| :--- | :--- | :--- |
| **Identificadores (PK, FK, Tenant)** | `UUID NOT NULL` | **PROHIBIDO** `SERIAL`/`BIGSERIAL`/`gen_random_uuid()` (`SHR-02`). PK con prefijo `pk_<tabla>`. |
| **Control Concurrencia** | `version BIGINT NOT NULL DEFAULT 0` | Obligatorio en Aggregate Root (`TRX-02`). Check `chk_<tabla>_version_non_neg CHECK (version >= 0)`. |
| **Marcas de Tiempo** | `TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()` | **PROHIBIDO** `TIMESTAMP` sin zona horaria. Check `chk_<tabla>_updated_coherence CHECK (updated_at >= created_at)`. |
| **Estados / Enums** | `<module_id>.<entity>_status NOT NULL DEFAULT '...'` | Declarado en Sección 1 como `CREATE TYPE <module_id>.<entity>_status AS ENUM (...)`. Mapeado en JPA con `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. **PROHIBIDO** checks redundantes `chk_<tabla>_status_valid` o `VARCHAR` para estados. |
| **Importes Monetarios** | `NUMERIC(p, s) NOT NULL` | **PROHIBIDO** `FLOAT`/`DOUBLE`/`REAL`. Check `chk_<tabla>_<campo>_pos CHECK (campo >= 0.00)`. |
| **Divisas / Monedas** | `shared.currency NOT NULL DEFAULT 'EUR'` | Dominio tipado transversal provisto en `shared`. |
| **Emails de Contacto** | `shared.email NOT NULL` | Dominio tipado transversal provisto en `shared`. |
| **Códigos de Negocio** | `shared.kebab_code NOT NULL` | Dominio tipado para identificadores legibles (*slugs*). |
| **Almacenamiento Físico** | Cláusula `) WITH (fillfactor = 90);` | En toda tabla transaccional de negocio para mitigar fragmentación HOT. |

## 4. PLANTILLA CANÓNICA DE CÓDIGO (`V<N>__init_<module_id>_tables.sql`)

```sql
-- =============================================================================
-- V<N>__init_<module_id>_tables.sql
-- Bounded Context: <MODULE_NAME>
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS <module_id>;
SET search_path TO <module_id>, shared, public;

-- -----------------------------------------------------------------------------
-- 1. TIPOS ENUMERADOS
-- -----------------------------------------------------------------------------
CREATE TYPE <module_id>.<entity>_status AS ENUM (
    'DRAFT',
    'ISSUED',
    'PAID',
    'CANCELLED'
);

-- -----------------------------------------------------------------------------
-- 2. TABLA: <agg_table> (Aggregate Root)
-- -----------------------------------------------------------------------------
CREATE TABLE <module_id>.<agg_table> (
    version            BIGINT                         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ                    NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ                    NOT NULL DEFAULT clock_timestamp(),
    deleted_at         TIMESTAMPTZ,
    status             <module_id>.<entity>_status    NOT NULL DEFAULT 'DRAFT',
    id                 UUID                           NOT NULL,
    tenant_id          UUID                           NOT NULL,
    <ext_mod>_id       UUID                           NOT NULL,
    code               shared.kebab_code              NOT NULL,
    total_amount       NUMERIC(12, 2)                 NOT NULL,
    currency           shared.currency                NOT NULL DEFAULT 'EUR',
    recipient_email    shared.email                   NOT NULL,

    CONSTRAINT pk_<agg_table>                    PRIMARY KEY (id),
    CONSTRAINT chk_<agg_table>_version_non_neg   CHECK (version >= 0),
    CONSTRAINT chk_<agg_table>_updated_coherence CHECK (updated_at >= created_at),
    CONSTRAINT chk_<agg_table>_deleted_coherence CHECK (deleted_at IS NULL OR deleted_at >= created_at),
    CONSTRAINT chk_<agg_table>_amount_pos        CHECK (total_amount >= 0.00)
) WITH (
    fillfactor = 90
);

-- Índices operativos y de unicidad
CREATE UNIQUE INDEX uq_<agg_table>_tenant_code ON <module_id>.<agg_table> (tenant_id, code) WHERE deleted_at IS NULL;
CREATE INDEX idx_<agg_table>_tenant_pagination ON <module_id>.<agg_table> (tenant_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_<agg_table>_<ext_mod>         ON <module_id>.<agg_table> (<ext_mod>_id);
CREATE INDEX idx_<agg_table>_status            ON <module_id>.<agg_table> (status, created_at DESC) WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- 3. TABLA: <child_table> (Entidad Subordinada)
-- -----------------------------------------------------------------------------
CREATE TABLE <module_id>.<child_table> (
    created_at         TIMESTAMPTZ                    NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ                    NOT NULL DEFAULT clock_timestamp(),
    id                 UUID                           NOT NULL,
    tenant_id          UUID                           NOT NULL,
    <agg>_id           UUID                           NOT NULL,
    line_number        INTEGER                        NOT NULL DEFAULT 1,
    unit_amount        NUMERIC(12, 2)                 NOT NULL,
    quantity           INTEGER                        NOT NULL DEFAULT 1,

    CONSTRAINT pk_<child_table>                  PRIMARY KEY (id),
    CONSTRAINT fk_<child_table>_<agg>            FOREIGN KEY (<agg>_id) REFERENCES <module_id>.<agg_table> (id) ON DELETE CASCADE,
    CONSTRAINT uq_<child_table>_line             UNIQUE (<agg>_id, line_number),
    CONSTRAINT chk_<child_table>_updated_coherent CHECK (updated_at >= created_at),
    CONSTRAINT chk_<child_table>_qty_pos         CHECK (quantity > 0)
) WITH (
    fillfactor = 90
);

-- Índices de entidad subordinada
CREATE INDEX idx_<child_table>_<agg>   ON <module_id>.<child_table> (<agg>_id);
CREATE INDEX idx_<child_table>_tenant ON <module_id>.<child_table> (tenant_id);

-- -----------------------------------------------------------------------------
-- 4. POLÍTICAS DE SEGURIDAD (RLS)
-- -----------------------------------------------------------------------------
ALTER TABLE <module_id>.<agg_table>          ENABLE ROW LEVEL SECURITY;
ALTER TABLE <module_id>.<agg_table>          FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_<module_id>_<agg_table> ON <module_id>.<agg_table>
    USING (tenant_id = (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (tenant_id = (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid));

ALTER TABLE <module_id>.<child_table>        ENABLE ROW LEVEL SECURITY;
ALTER TABLE <module_id>.<child_table>        FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_<module_id>_<child_table> ON <module_id>.<child_table>
    USING (tenant_id = (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (tenant_id = (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid));

-- -----------------------------------------------------------------------------
-- 5. PERMISOS
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = '<app_role>') THEN
        GRANT USAGE ON SCHEMA <module_id> TO <app_role>;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA <module_id> TO <app_role>;
        ALTER DEFAULT PRIVILEGES IN SCHEMA <module_id> GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO <app_role>;
    END IF;
END
$$;
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*Migration*" --tests "*PersistenceRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generado**: Ruta del archivo `V<N>__*.sql`, esquema `<module_id>`, tipos `ENUM`, tablas de negocio y políticas RLS aplicadas.
    * **Intactos**: Migraciones previas (`V1..V<N-1>`).
    * **Recordatorio**: Queda pendiente generar los enums (`generate-domain-enums`) y las entidades JPA (`generate-jpa-entities`).

