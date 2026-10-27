package com.template.api.shared.infrastructure.adapter.out.persistence.jdbc;

import com.template.api.shared.application.port.out.IdempotencyGate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcIdempotencyGate implements IdempotencyGate {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public boolean tryAcquire(String moduleSchema, UUID eventId, String consumerName) {
        String tableName = resolveTable(moduleSchema);
        String sql = """
            INSERT INTO %s (event_id, consumer_name, processed_at)
            VALUES (:eventId, :consumerName, clock_timestamp())
            ON CONFLICT (event_id, consumer_name) DO NOTHING
        """.formatted(tableName);
        int rows = jdbcTemplate.update(sql, Map.of(
                "eventId", eventId,
                "consumerName", consumerName != null ? consumerName : "default"
        ));
        return rows > 0;
    }

    @Override
    public void release(String moduleSchema, UUID eventId, String consumerName) {
        String tableName = resolveTable(moduleSchema);
        if (consumerName != null) {
            String sql = "DELETE FROM " + tableName + " WHERE event_id = :eventId AND consumer_name = :consumerName";
            jdbcTemplate.update(sql, Map.of(
                    "eventId", eventId,
                    "consumerName", consumerName
            ));
        } else {
            String sql = "DELETE FROM " + tableName + " WHERE event_id = :eventId";
            jdbcTemplate.update(sql, Map.of("eventId", eventId));
        }
    }

    @Override
    public boolean release(UUID eventId) {
        String tableName = resolveTable(null);
        String sql = "DELETE FROM " + tableName + " WHERE event_id = :eventId";
        int rows = jdbcTemplate.update(sql, Map.of("eventId", eventId));
        return rows > 0;
    }

    @Override
    public boolean isProcessed(String moduleSchema, UUID eventId) {
        String tableName = resolveTable(moduleSchema);
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE event_id = :eventId";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("eventId", eventId), Integer.class);
        return count != null && count > 0;
    }

    private String resolveTable(String moduleSchema) {
        return "shared.processed_events";
    }
}
