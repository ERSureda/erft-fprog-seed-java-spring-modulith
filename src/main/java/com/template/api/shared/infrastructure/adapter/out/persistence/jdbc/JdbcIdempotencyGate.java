package com.template.api.shared.infrastructure.adapter.out.persistence.jdbc;

import com.template.api.shared.application.port.out.IdempotencyGate;
import com.template.api.shared.domain.exception.BaseException;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class JdbcIdempotencyGate implements IdempotencyGate {

    private static final String DEFAULT_CONSUMER = "default";
    private static final String DEFAULT_TABLE = "shared.processed_events";
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private static final String SQL_TRY_ACQUIRE = """
        INSERT INTO shared.processed_events (event_id, consumer_name, processed_at)
        VALUES (:eventId, :consumerName, clock_timestamp())
        ON CONFLICT (event_id, consumer_name) DO NOTHING
    """;

    private static final String SQL_RELEASE_BY_CONSUMER = """
        DELETE FROM shared.processed_events WHERE event_id = :eventId AND consumer_name = :consumerName
    """;

    private static final String SQL_RELEASE_BY_EVENT = """
        DELETE FROM shared.processed_events WHERE event_id = :eventId
    """;

    private static final String SQL_IS_PROCESSED = """
        SELECT EXISTS(SELECT 1 FROM shared.processed_events WHERE event_id = :eventId)
    """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean isolatedSchemas;

    @Autowired
    public JdbcIdempotencyGate(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${idempotency.isolated-schemas:false}") boolean isolatedSchemas
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.isolatedSchemas = isolatedSchemas;
    }

    public JdbcIdempotencyGate(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, false);
    }

    @Override
    public boolean tryAcquire(String moduleSchema, UUID eventId, String consumerName) {
        Objects.requireNonNull(eventId, "IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
        String table = resolveTable(moduleSchema);
        String sql = DEFAULT_TABLE.equals(table) ? SQL_TRY_ACQUIRE :
                """
                INSERT INTO %s (event_id, consumer_name, processed_at)
                VALUES (:eventId, :consumerName, clock_timestamp())
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """.formatted(table);
        try {
            int rows = jdbcTemplate.update(sql, Map.of(
                    "eventId", eventId,
                    "consumerName", (consumerName != null && !consumerName.isBlank()) ? consumerName : DEFAULT_CONSUMER
            ));
            return rows > 0;
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to acquire idempotency lock for event " + eventId, ex);
        }
    }

    @Override
    public void release(String moduleSchema, UUID eventId, String consumerName) {
        Objects.requireNonNull(eventId, "IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
        String table = resolveTable(moduleSchema);
        try {
            if (consumerName != null && !consumerName.isBlank()) {
                String sql = DEFAULT_TABLE.equals(table) ? SQL_RELEASE_BY_CONSUMER :
                        "DELETE FROM " + table + " WHERE event_id = :eventId AND consumer_name = :consumerName";
                jdbcTemplate.update(sql, Map.of(
                        "eventId", eventId,
                        "consumerName", consumerName
                ));
            } else {
                String sql = DEFAULT_TABLE.equals(table) ? SQL_RELEASE_BY_EVENT :
                        "DELETE FROM " + table + " WHERE event_id = :eventId";
                jdbcTemplate.update(sql, Map.of("eventId", eventId));
            }
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to release idempotency lock for event " + eventId, ex);
        }
    }

    @Override
    public boolean release(UUID eventId) {
        Objects.requireNonNull(eventId, "IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
        try {
            int rows = jdbcTemplate.update(SQL_RELEASE_BY_EVENT, Map.of("eventId", eventId));
            return rows > 0;
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to release idempotency lock for event " + eventId, ex);
        }
    }

    @Override
    public boolean isProcessed(String moduleSchema, UUID eventId) {
        Objects.requireNonNull(eventId, "IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
        String table = resolveTable(moduleSchema);
        String sql = DEFAULT_TABLE.equals(table) ? SQL_IS_PROCESSED :
                "SELECT EXISTS(SELECT 1 FROM " + table + " WHERE event_id = :eventId)";
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    sql,
                    Map.of("eventId", eventId),
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to check idempotency state for event " + eventId, ex);
        }
    }

    private String resolveTable(String moduleSchema) {
        if (!isolatedSchemas || moduleSchema == null || moduleSchema.isBlank()) {
            return DEFAULT_TABLE;
        }
        String trimmed = moduleSchema.trim();
        if ("default".equalsIgnoreCase(trimmed) || "shared".equalsIgnoreCase(trimmed)) {
            return DEFAULT_TABLE;
        }
        if (!SCHEMA_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("IDEMPOTENCY_INVALID_SCHEMA_NAME: " + trimmed);
        }
        return trimmed + ".processed_events";
    }
}
