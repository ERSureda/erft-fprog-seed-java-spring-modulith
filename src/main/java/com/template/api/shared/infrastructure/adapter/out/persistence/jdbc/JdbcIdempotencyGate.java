package com.template.api.shared.infrastructure.adapter.out.persistence.jdbc;

import com.template.api.shared.application.port.out.IdempotencyGate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcIdempotencyGate implements IdempotencyGate {

    private static final String DEFAULT_CONSUMER = "default";
    private static final String DEFAULT_TABLE = "shared.processed_events";

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

    @Override
    public boolean tryAcquire(String moduleSchema, UUID eventId, String consumerName) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        String table = resolveTable(moduleSchema);
        String sql = DEFAULT_TABLE.equals(table) ? SQL_TRY_ACQUIRE :
                """
                INSERT INTO %s (event_id, consumer_name, processed_at)
                VALUES (:eventId, :consumerName, clock_timestamp())
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """.formatted(table);
        int rows = jdbcTemplate.update(sql, Map.of(
                "eventId", eventId,
                "consumerName", (consumerName != null && !consumerName.isBlank()) ? consumerName : DEFAULT_CONSUMER
        ));
        return rows > 0;
    }

    @Override
    public void release(String moduleSchema, UUID eventId, String consumerName) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        String table = resolveTable(moduleSchema);
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
    }

    @Override
    public boolean release(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        int rows = jdbcTemplate.update(SQL_RELEASE_BY_EVENT, Map.of("eventId", eventId));
        return rows > 0;
    }

    @Override
    public boolean isProcessed(String moduleSchema, UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        String table = resolveTable(moduleSchema);
        String sql = DEFAULT_TABLE.equals(table) ? SQL_IS_PROCESSED :
                "SELECT EXISTS(SELECT 1 FROM " + table + " WHERE event_id = :eventId)";
        Boolean exists = jdbcTemplate.queryForObject(
                sql,
                Map.of("eventId", eventId),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }

    private String resolveTable(String moduleSchema) {
        if (moduleSchema != null && !moduleSchema.isBlank() && !"default".equalsIgnoreCase(moduleSchema.trim()) && !"shared".equalsIgnoreCase(moduleSchema.trim())) {
            return moduleSchema.trim() + ".processed_events";
        }
        return DEFAULT_TABLE;
    }
}
