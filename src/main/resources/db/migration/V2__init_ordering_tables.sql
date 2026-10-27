-- =============================================================================
-- V2__init_ordering_tables.sql
-- Bounded Context: ORDERING
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
    version            BIGINT                      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ                 NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ                 NOT NULL DEFAULT clock_timestamp(),
    deleted_at         TIMESTAMPTZ,
    status             ordering.order_status       NOT NULL DEFAULT 'PENDING',
    id                 UUID                        NOT NULL,
    tenant_id          UUID                        NOT NULL,
    customer_id        UUID                        NOT NULL,
    amount             NUMERIC(12, 2)              NOT NULL,
    currency           shared.currency             NOT NULL DEFAULT 'EUR',

    CONSTRAINT pk_orders                    PRIMARY KEY (id),
    CONSTRAINT chk_orders_version_non_neg   CHECK (version >= 0),
    CONSTRAINT chk_orders_updated_coherence CHECK (updated_at >= created_at),
    CONSTRAINT chk_orders_deleted_coherence CHECK (deleted_at IS NULL OR deleted_at >= created_at),
    CONSTRAINT chk_orders_amount_pos        CHECK (amount >= 0.00)
) WITH (
    fillfactor = 90
);

-- Índices operativos y de consulta
CREATE INDEX idx_orders_tenant_pagination ON ordering.orders (tenant_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_customer ON ordering.orders (customer_id);
CREATE INDEX idx_orders_status ON ordering.orders (status, created_at DESC)
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- 3. POLÍTICAS DE SEGURIDAD (RLS)
-- -----------------------------------------------------------------------------
ALTER TABLE ordering.orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE ordering.orders FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_ordering_orders ON ordering.orders
    USING (tenant_id = (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid))
    WITH CHECK (tenant_id = (SELECT nullif(current_setting('app.current_tenant_id', true), '')::uuid));

-- -----------------------------------------------------------------------------
-- 4. PERMISOS
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'template_app') THEN
        GRANT USAGE ON SCHEMA ordering TO template_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ordering TO template_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA ordering GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO template_app;
    END IF;
END
$$;
