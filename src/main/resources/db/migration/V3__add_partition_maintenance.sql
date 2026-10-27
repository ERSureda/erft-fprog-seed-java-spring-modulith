-- =============================================================================
-- V3__add_partition_maintenance.sql
-- Gestión y Aprovisionamiento Automatizado de Particiones Mensuales
-- =============================================================================

SET search_path TO shared, public;

-- -----------------------------------------------------------------------------
-- 1. CATÁLOGO: partitioned_tables
-- -----------------------------------------------------------------------------
CREATE TABLE shared.partitioned_tables (
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    is_audit_table     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    schema_name        VARCHAR(64)  NOT NULL,
    table_name         VARCHAR(64)  NOT NULL,
    partition_column   VARCHAR(64)  NOT NULL,
    partition_prefix   VARCHAR(32)  NOT NULL DEFAULT 'y',
    pre_create_months  INTEGER      NOT NULL DEFAULT 3,
    retention_months   INTEGER,

    CONSTRAINT pk_partitioned_tables
        PRIMARY KEY (schema_name, table_name),
    CONSTRAINT chk_partitioned_tables_pre_create
        CHECK (pre_create_months >= 1),
    CONSTRAINT chk_partitioned_tables_retention
        CHECK (retention_months IS NULL OR retention_months >= 1)
);

-- Registro de la bitácora centralizada de auditoría (OUT-08)
INSERT INTO shared.partitioned_tables (
    schema_name,
    table_name,
    partition_column,
    pre_create_months,
    is_audit_table
) VALUES (
    'shared',
    'audit_log',
    'occurred_at',
    3,
    TRUE
) ON CONFLICT (schema_name, table_name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. PROCEDIMIENTO: sp_maintain_monthly_partitions
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE shared.sp_maintain_monthly_partitions(
    p_lookahead_months  INTEGER DEFAULT 3,
    p_lookbehind_months INTEGER DEFAULT 1
)
LANGUAGE plpgsql
AS $$
DECLARE
    r RECORD;
    v_utc_now      TIMESTAMP;
    v_target_month TIMESTAMP;
    v_start_month  TIMESTAMP;
    v_end_month    TIMESTAMP;
    v_part_start   TIMESTAMP;
    v_part_end     TIMESTAMP;
    v_start_str    VARCHAR(32);
    v_end_str      VARCHAR(32);
    v_part_name    VARCHAR(128);
    v_full_name    VARCHAR(256);
    v_sql          TEXT;
    v_has_app_role BOOLEAN;
BEGIN
    IF p_lookahead_months < 1 THEN
        RAISE EXCEPTION 'p_lookahead_months debe ser al menos 1';
    END IF;

    SELECT EXISTS (SELECT FROM pg_roles WHERE rolname = 'template_app')
    INTO v_has_app_role;

    v_utc_now     := timezone('UTC', clock_timestamp());
    v_start_month := date_trunc('month', v_utc_now)
                     - (p_lookbehind_months || ' month')::interval;
    v_end_month   := date_trunc('month', v_utc_now)
                     + (p_lookahead_months || ' month')::interval;

    FOR r IN (
        SELECT schema_name, table_name, partition_prefix, is_audit_table
        FROM shared.partitioned_tables
        WHERE is_active = TRUE
        ORDER BY schema_name, table_name
    ) LOOP
        v_target_month := v_start_month;

        WHILE v_target_month <= v_end_month LOOP
            v_part_start := v_target_month;
            v_part_end   := v_target_month + INTERVAL '1 month';

            v_start_str := to_char(v_part_start, 'YYYY-MM-DD 00:00:00')
                           || '+00';
            v_end_str   := to_char(v_part_end, 'YYYY-MM-DD 00:00:00')
                           || '+00';

            v_part_name := r.table_name || '_' || r.partition_prefix ||
                           to_char(v_part_start, 'YYYY') || 'm' ||
                           to_char(v_part_start, 'MM');
            v_full_name := r.schema_name || '.' || v_part_name;

            IF to_regclass(quote_ident(r.schema_name) || '.' ||
                           quote_ident(v_part_name)) IS NULL THEN
                v_sql := format(
                    'CREATE TABLE IF NOT EXISTS %I.%I PARTITION OF %I.%I ' ||
                    'FOR VALUES FROM (%L) TO (%L);',
                    r.schema_name, v_part_name,
                    r.schema_name, r.table_name,
                    v_start_str, v_end_str
                );
                EXECUTE v_sql;

                IF v_has_app_role THEN
                    EXECUTE format(
                        'GRANT SELECT, INSERT, UPDATE, DELETE ' ||
                        'ON %I.%I TO template_app;',
                        r.schema_name, v_part_name
                    );
                END IF;

                IF r.is_audit_table THEN
                    EXECUTE format(
                        'REVOKE UPDATE, DELETE, TRUNCATE ' ||
                        'ON %I.%I FROM PUBLIC;',
                        r.schema_name, v_part_name
                    );
                    IF v_has_app_role THEN
                        EXECUTE format(
                            'REVOKE UPDATE, DELETE, TRUNCATE ' ||
                            'ON %I.%I FROM template_app;',
                            r.schema_name, v_part_name
                        );
                    END IF;
                END IF;
            END IF;

            v_target_month := v_target_month + INTERVAL '1 month';
        END LOOP;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- 3. VISTA: v_default_partitions_health
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW shared.v_default_partitions_health AS
SELECT
    'shared'::VARCHAR(64)            AS schema_name,
    'audit_log'::VARCHAR(64)         AS table_name,
    'audit_log_default'::VARCHAR(64) AS default_partition_name,
    EXISTS (
        SELECT 1 FROM shared.audit_log_default LIMIT 1
    )                                AS has_default_data,
    CASE
        WHEN EXISTS (SELECT 1 FROM shared.audit_log_default LIMIT 1)
            THEN 'CRITICAL_DEFAULT_VIOLATION'
        ELSE 'HEALTHY'
    END                              AS status,
    clock_timestamp()                AS last_checked_at;

-- -----------------------------------------------------------------------------
-- 4. PERMISOS
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'template_app') THEN
        GRANT SELECT ON shared.partitioned_tables TO template_app;
        GRANT SELECT ON shared.v_default_partitions_health TO template_app;
        GRANT EXECUTE ON PROCEDURE
            shared.sp_maintain_monthly_partitions(INTEGER, INTEGER)
            TO template_app;
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 5. EJECUCIÓN INICIAL
-- -----------------------------------------------------------------------------
CALL shared.sp_maintain_monthly_partitions(3, 1);
