-- =============================================================================
-- V2__init_ordering_tables.sql
-- Bounded Context: ORDERING
-- Cumplimiento normativo: OUT-06, OUT-07, OUT-08, TRX-02, SHR-02, SHR-03
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS ordering;
SET search_path TO ordering, shared, public;

-- -----------------------------------------------------------------------------
-- 1. TIPOS ENUMERADOS
-- -----------------------------------------------------------------------------
CREATE TYPE ordering.order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'SHIPPED',
    'CANCELLED'
);

-- -----------------------------------------------------------------------------
-- 2. TABLA: orders (Aggregate Root)
-- -----------------------------------------------------------------------------
CREATE TABLE ordering.orders (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    customer_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency shared.currency NOT NULL DEFAULT 'EUR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    deleted_at TIMESTAMPTZ,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT chk_orders_ver_pos CHECK (version >= 0),
    CONSTRAINT chk_orders_upd_coh CHECK (updated_at >= created_at),
    CONSTRAINT chk_orders_del_time CHECK (deleted_at IS NULL OR deleted_at >= created_at),
    CONSTRAINT chk_orders_amount_pos CHECK (amount >= 0.00),
    CONSTRAINT chk_orders_status_valid CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'CANCELLED'))
) WITH (
    fillfactor = 90
);

COMMENT ON TABLE ordering.orders IS 'Aggregate Root que gestiona el ciclo de vida de pedidos.';
COMMENT ON COLUMN ordering.orders.id IS 'Identificador UUIDv7 del agregado.';
COMMENT ON COLUMN ordering.orders.tenant_id IS 'Identificador de inquilino para aislamiento RLS.';
COMMENT ON COLUMN ordering.orders.customer_id IS 'Referencia escalar al cliente sin FOREIGN KEY (OUT-06).';

-- Índices operativos y de consulta
CREATE INDEX idx_orders_tenant_pagination ON ordering.orders (tenant_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_customer ON ordering.orders (tenant_id, customer_id);
CREATE INDEX idx_orders_status ON ordering.orders (tenant_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- 3. AUDIT LOG (Particionado Mensual)
-- -----------------------------------------------------------------------------
CREATE TABLE ordering.audit_log (
    id UUID NOT NULL,
    tenant_id UUID,
    actor_id UUID NOT NULL,
    actor_type shared.actor_type NOT NULL DEFAULT 'SYSTEM',
    entity_name VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(120),
    ip_address INET,
    changes JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT pk_ordering_audit PRIMARY KEY (id, occurred_at),
    CONSTRAINT chk_ordering_audit_changes_object CHECK (jsonb_typeof(changes) = 'object')
) PARTITION BY RANGE (occurred_at);

COMMENT ON TABLE ordering.audit_log IS 'Bitácora inmutable de pistas de auditoría particionada por rango temporal.';
COMMENT ON COLUMN ordering.audit_log.id IS 'Identificador UUIDv7 del registro de auditoría.';
COMMENT ON COLUMN ordering.audit_log.tenant_id IS 'Identificador de inquilino para aislamiento RLS.';

-- Partición por defecto
CREATE TABLE ordering.audit_log_default
    PARTITION OF ordering.audit_log DEFAULT;

-- Particiones mensuales pre-aprovisionadas
CREATE TABLE ordering.audit_log_y2026m10 PARTITION OF ordering.audit_log
    FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00');
CREATE TABLE ordering.audit_log_y2026m11 PARTITION OF ordering.audit_log
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');
CREATE TABLE ordering.audit_log_y2026m12 PARTITION OF ordering.audit_log
    FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');
CREATE TABLE ordering.audit_log_y2027m01 PARTITION OF ordering.audit_log
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00');

-- Índices de consulta de auditoría
CREATE INDEX idx_ordering_audit_tenant_lookup ON ordering.audit_log (tenant_id, occurred_at DESC)
    WHERE tenant_id IS NOT NULL;
CREATE INDEX idx_ordering_audit_entity_lookup ON ordering.audit_log (entity_name, entity_id, occurred_at DESC);
CREATE INDEX idx_ordering_audit_actor_lookup ON ordering.audit_log (actor_id, occurred_at DESC);

-- -----------------------------------------------------------------------------
-- 4. TABLA: outbox_events (Transactional Outbox Modular)
-- -----------------------------------------------------------------------------
CREATE TABLE ordering.outbox_events (
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

    CONSTRAINT pk_ordering_outbox_events                 PRIMARY KEY (event_id),
    CONSTRAINT chk_ordering_outbox_events_retry_count    CHECK (retry_count >= 0),
    CONSTRAINT chk_ordering_outbox_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_ordering_outbox_events_delivered      CHECK ((status = 'DELIVERED') = (delivered_at IS NOT NULL)),
    CONSTRAINT chk_ordering_outbox_events_processing     CHECK (status <> 'PROCESSING' OR locked_at IS NOT NULL),
    CONSTRAINT chk_ordering_outbox_events_failed         CHECK (status <> 'FAILED' OR (next_retry_at IS NOT NULL AND last_error IS NOT NULL)),
    CONSTRAINT chk_ordering_outbox_events_dead_letter    CHECK (status <> 'DEAD_LETTER' OR last_error IS NOT NULL)
) WITH (
    fillfactor = 80,
    autovacuum_vacuum_scale_factor = 0.01,
    autovacuum_vacuum_cost_limit = 2000,
    autovacuum_analyze_scale_factor = 0.02
);

COMMENT ON TABLE ordering.outbox_events IS 'Bandeja de salida transaccional del módulo Ordering para publicación atómica de eventos.';
COMMENT ON COLUMN ordering.outbox_events.event_id IS 'Identificador UUIDv7 del evento Outbox.';

-- Índices operativos (SKIP LOCKED y reintentos)
CREATE INDEX idx_ordering_outbox_events_pending
    ON ordering.outbox_events (created_at ASC, event_id ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_ordering_outbox_events_processing
    ON ordering.outbox_events (status, created_at ASC)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_ordering_outbox_events_retry
    ON ordering.outbox_events (next_retry_at ASC, event_id ASC)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;

CREATE INDEX idx_ordering_outbox_events_stale_locks
    ON ordering.outbox_events (locked_at ASC)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_ordering_outbox_events_purge
    ON ordering.outbox_events (delivered_at ASC)
    WHERE status = 'DELIVERED';

CREATE INDEX idx_ordering_outbox_events_dlq
    ON ordering.outbox_events (created_at DESC)
    WHERE status = 'DEAD_LETTER';

CREATE INDEX idx_ordering_outbox_events_aggregate
    ON ordering.outbox_events (aggregate_id, created_at DESC);

CREATE INDEX idx_ordering_outbox_events_tenant
    ON ordering.outbox_events (tenant_id, created_at DESC)
    WHERE tenant_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 5. TABLA: processed_events (Compuerta de Idempotencia Modular)
-- -----------------------------------------------------------------------------
CREATE TABLE ordering.processed_events (
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    event_id      UUID         NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,

    CONSTRAINT pk_ordering_processed_events PRIMARY KEY (event_id, consumer_name)
) WITH (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.02
);

COMMENT ON TABLE ordering.processed_events IS 'Compuerta de idempotencia del módulo Ordering para deduplicación de eventos entrantes.';
COMMENT ON COLUMN ordering.processed_events.event_id IS 'Identificador UUID del evento procesado.';
COMMENT ON COLUMN ordering.processed_events.consumer_name IS 'Identificador del consumidor que procesó el evento.';

-- Índices de consulta y purga TTL
CREATE INDEX idx_ordering_processed_events_purge
    ON ordering.processed_events (processed_at ASC);

CREATE INDEX idx_ordering_processed_events_consumer
    ON ordering.processed_events (consumer_name, processed_at DESC);

-- -----------------------------------------------------------------------------
-- 6. POLÍTICAS DE SEGURIDAD (RLS)
-- -----------------------------------------------------------------------------
ALTER TABLE ordering.orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE ordering.orders FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_ordering_orders ON ordering.orders
    USING (tenant_id = (SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (tenant_id = (SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid));

ALTER TABLE ordering.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE ordering.audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_ordering_audit_log ON ordering.audit_log
    USING (tenant_id IS NOT DISTINCT FROM (SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (tenant_id IS NOT DISTINCT FROM (SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid));

-- -----------------------------------------------------------------------------
-- 7. PERMISOS E INMUTABILIDAD
-- -----------------------------------------------------------------------------
REVOKE UPDATE, DELETE, TRUNCATE ON ordering.audit_log FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'template_app') THEN
        GRANT USAGE ON SCHEMA ordering TO template_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ordering TO template_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA ordering GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO template_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON ordering.audit_log FROM template_app;
    END IF;
END
$$;
