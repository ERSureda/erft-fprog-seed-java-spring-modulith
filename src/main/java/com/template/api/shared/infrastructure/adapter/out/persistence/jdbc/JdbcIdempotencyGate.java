package com.template.api.shared.infrastructure.adapter.out.persistence.jdbc;

import com.template.api.shared.application.port.out.IdempotencyGate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de infraestructura JDBC para el control transversal de idempotencia.
 * <p>
 * Implementa el patrón Idempotent Consumer sobre {@code shared.processed_events} para garantizar
 * procesamiento exactamente una vez (effectively-once) en arquitecturas dirigidas por eventos (TRX-05).
 * En la fase de monolito modular con Spring Modulith (ADR_002), todos los módulos comparten la tabla técnica
 * centralizada en el esquema {@code shared}.
 */
@Component
@RequiredArgsConstructor
public class JdbcIdempotencyGate implements IdempotencyGate {

    private static final String DEFAULT_CONSUMER = "default";

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
        int rows = jdbcTemplate.update(SQL_TRY_ACQUIRE, Map.of(
                "eventId", eventId,
                "consumerName", (consumerName != null && !consumerName.isBlank()) ? consumerName : DEFAULT_CONSUMER
        ));
        return rows > 0;
    }

    @Override
    public void release(String moduleSchema, UUID eventId, String consumerName) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (consumerName != null && !consumerName.isBlank()) {
            jdbcTemplate.update(SQL_RELEASE_BY_CONSUMER, Map.of(
                    "eventId", eventId,
                    "consumerName", consumerName
            ));
        } else {
            jdbcTemplate.update(SQL_RELEASE_BY_EVENT, Map.of("eventId", eventId));
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
        Boolean exists = jdbcTemplate.queryForObject(
                SQL_IS_PROCESSED,
                Map.of("eventId", eventId),
                Boolean.class
        );
        return Boolean.TRUE.equals(exists);
    }
}
