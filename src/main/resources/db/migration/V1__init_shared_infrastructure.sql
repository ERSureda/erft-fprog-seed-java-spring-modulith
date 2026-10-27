-- =============================================================================
-- V1__init_shared_infrastructure.sql
-- Infraestructura Transversal y Kernel Compartido
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. EXTENSIONES DEL MOTOR
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "btree_gist";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- -----------------------------------------------------------------------------
-- 2. ESQUEMA TRANSVERSAL
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS shared;
SET search_path TO shared, public;

-- -----------------------------------------------------------------------------
-- 3. DOMINIOS Y VALIDACIONES DE TIPO
-- -----------------------------------------------------------------------------
-- Código ISO-4217 de divisa (tres letras mayúsculas)
CREATE DOMAIN shared.currency AS VARCHAR(3)
    CONSTRAINT chk_currency_iso_format CHECK (VALUE ~ '^[A-Z]{3}$');

-- Teléfono en formato internacional E.164 (+ seguido de 1 a 14 dígitos)
CREATE DOMAIN shared.phone_e164 AS VARCHAR(20)
    CONSTRAINT chk_phone_e164_format CHECK (VALUE ~ '^\+[1-9]\d{1,14}$');

-- Formato canónico de correo electrónico RFC 5322 simplificado
CREATE DOMAIN shared.email AS VARCHAR(255)
    CONSTRAINT chk_email_format CHECK (VALUE ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$');

-- Identificador semántico en kebab-case
CREATE DOMAIN shared.kebab_code AS VARCHAR(64)
    CONSTRAINT chk_kebab_code_format CHECK (VALUE ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

-- Código de país ISO 3166-1 alfa-2 (dos letras mayúsculas)
CREATE DOMAIN shared.country_code AS VARCHAR(2)
    CONSTRAINT chk_country_code_format CHECK (VALUE ~ '^[A-Z]{2}$');

-- Código de idioma ISO 639-1 / BCP-47
CREATE DOMAIN shared.language AS VARCHAR(5)
    CONSTRAINT chk_language_format CHECK (VALUE ~ '^[a-z]{2}(-[A-Z]{2})?$');

-- Hash criptográfico SHA-256 en hexadecimal (64 caracteres minúsculos)
CREATE DOMAIN shared.sha256_hex AS VARCHAR(64)
    CONSTRAINT chk_sha256_hex_format CHECK (VALUE ~ '^[0-9a-f]{64}$');

-- -----------------------------------------------------------------------------
-- 4. TIPOS ENUMERADOS GLOBALES
-- -----------------------------------------------------------------------------
CREATE TYPE shared.actor_type AS ENUM (
    'ANONYMOUS',
    'CLIENT',
    'TENANT_USER',
    'SAAS_ADMIN',
    'SYSTEM',
    'API_CLIENT'
);

CREATE TYPE shared.outbox_status AS ENUM (
    'PENDING',
    'PROCESSING',
    'DELIVERED',
    'FAILED',
    'DEAD_LETTER'
);

-- -----------------------------------------------------------------------------
-- 5. TABLA: outbox_events (Transactional Outbox Transversal)
-- -----------------------------------------------------------------------------
CREATE TABLE shared.outbox_events (
    created_at     TIMESTAMPTZ          NOT NULL DEFAULT clock_timestamp(),
    next_retry_at  TIMESTAMPTZ,
    locked_at      TIMESTAMPTZ,
    delivered_at   TIMESTAMPTZ,
    status         shared.outbox_status NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER              NOT NULL DEFAULT 0,
    event_id       UUID                 NOT NULL,
    tenant_id      UUID,
    payload        JSONB                NOT NULL,
    aggregate_type VARCHAR(64)          NOT NULL,
    aggregate_id   VARCHAR(64)          NOT NULL,
    event_type     VARCHAR(128)         NOT NULL,
    correlation_id VARCHAR(120),
    last_error     TEXT,

    CONSTRAINT pk_outbox_events                 PRIMARY KEY (event_id),
    CONSTRAINT chk_outbox_events_retry_count    CHECK (retry_count >= 0),
    CONSTRAINT chk_outbox_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_outbox_events_delivered      CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL)),
    CONSTRAINT chk_outbox_events_processing     CHECK (status <> 'PROCESSING' OR locked_at IS NOT NULL),
    CONSTRAINT chk_outbox_events_failed         CHECK (status <> 'FAILED' OR (next_retry_at IS NOT NULL AND last_error IS NOT NULL)),
    CONSTRAINT chk_outbox_events_dead_letter    CHECK (status <> 'DEAD_LETTER' OR last_error IS NOT NULL)
) WITH (
    fillfactor = 80,
    autovacuum_vacuum_scale_factor = 0.01,
    autovacuum_vacuum_cost_limit = 2000,
    autovacuum_analyze_scale_factor = 0.02
);

-- Índices operativos (SKIP LOCKED y reintentos)
CREATE INDEX idx_outbox_events_pending
    ON shared.outbox_events (created_at ASC, event_id ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_processing
    ON shared.outbox_events (status, created_at ASC)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_outbox_events_retry
    ON shared.outbox_events (next_retry_at ASC, event_id ASC)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;

CREATE INDEX idx_outbox_events_stale_locks
    ON shared.outbox_events (locked_at ASC)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_outbox_events_purge
    ON shared.outbox_events (delivered_at ASC)
    WHERE status = 'DELIVERED';

CREATE INDEX idx_outbox_events_dlq
    ON shared.outbox_events (created_at DESC)
    WHERE status = 'DEAD_LETTER';

CREATE INDEX idx_outbox_events_aggregate
    ON shared.outbox_events (aggregate_id, created_at DESC);

CREATE INDEX idx_outbox_events_tenant
    ON shared.outbox_events (tenant_id, created_at DESC)
    WHERE tenant_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 6. TABLA: processed_events (Compuerta de Idempotencia Transversal)
-- -----------------------------------------------------------------------------
CREATE TABLE shared.processed_events (
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    event_id      UUID         NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,

    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_name)
) WITH (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.02
);

-- Índices de consulta y purga TTL
CREATE INDEX idx_processed_events_purge
    ON shared.processed_events (processed_at ASC);

CREATE INDEX idx_processed_events_consumer
    ON shared.processed_events (consumer_name, processed_at DESC);

-- -----------------------------------------------------------------------------
-- 7. AUDIT LOG (Particionado Mensual Transversal)
-- -----------------------------------------------------------------------------
CREATE TABLE shared.audit_log (
    occurred_at    TIMESTAMPTZ       NOT NULL DEFAULT clock_timestamp(),
    actor_type     shared.actor_type NOT NULL,
    id             UUID              NOT NULL,
    actor_id       UUID              NOT NULL,
    entity_id      UUID              NOT NULL,
    tenant_id      UUID,
    changes        JSONB             NOT NULL,
    action         VARCHAR(64)       NOT NULL,
    entity_name    VARCHAR(64)       NOT NULL,
    ip_address     INET,
    correlation_id VARCHAR(120),

    CONSTRAINT pk_audit_log PRIMARY KEY (id, occurred_at),
    CONSTRAINT chk_audit_log_changes_object CHECK (jsonb_typeof(changes) = 'object')
) PARTITION BY RANGE (occurred_at);

-- Partición por defecto
CREATE TABLE shared.audit_log_default
    PARTITION OF shared.audit_log DEFAULT;

-- Particiones mensuales pre-aprovisionadas
CREATE TABLE shared.audit_log_y2026m10 PARTITION OF shared.audit_log
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');
CREATE TABLE shared.audit_log_y2026m11 PARTITION OF shared.audit_log
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
CREATE TABLE shared.audit_log_y2026m12 PARTITION OF shared.audit_log
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');
CREATE TABLE shared.audit_log_y2027m01 PARTITION OF shared.audit_log
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');

-- Índices de consulta de auditoría
CREATE INDEX idx_shared_audit_tenant_lookup ON shared.audit_log (tenant_id, occurred_at DESC)
    WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_shared_audit_entity_lookup ON shared.audit_log (entity_name, entity_id, occurred_at DESC);
CREATE INDEX idx_shared_audit_actor_lookup ON shared.audit_log (actor_id, occurred_at DESC);

-- -----------------------------------------------------------------------------
-- 8. POLÍTICAS DE SEGURIDAD (RLS)
-- -----------------------------------------------------------------------------
ALTER TABLE shared.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE shared.audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_shared_audit_log ON shared.audit_log
    USING (tenant_id IS NOT DISTINCT FROM (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (tenant_id IS NOT DISTINCT FROM (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid));

-- -----------------------------------------------------------------------------
-- 9. PERMISOS E INMUTABILIDAD
-- -----------------------------------------------------------------------------
REVOKE UPDATE, DELETE, TRUNCATE ON shared.audit_log FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'template_app') THEN
        GRANT USAGE ON SCHEMA shared TO template_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA shared TO template_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA shared GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO template_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON shared.audit_log FROM template_app;
    END IF;
END
$$;
