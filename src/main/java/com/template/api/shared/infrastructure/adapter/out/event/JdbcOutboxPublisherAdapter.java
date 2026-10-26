package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.port.out.OutboxPublisherPort;
import com.template.api.shared.application.port.out.UuidGeneratorPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JdbcOutboxPublisherAdapter implements OutboxPublisherPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UuidGeneratorPort uuidGenerator;

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        publish(null, resolveAggregateType(event), resolveAggregateId(event), event);
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            if (event != null) {
                publish(null, resolveAggregateType(event), resolveAggregateId(event), event);
            }
        }
    }

    @Override
    public void publish(String moduleSchema, String aggregateType, UUID aggregateId, DomainEvent event) {
        if (event == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            String correlationId = ExecutionContextHolder.get() != null
                    ? ExecutionContextHolder.get().correlationId()
                    : null;
            UUID tenantId = ExecutionContextHolder.getTenantId();

            String table = resolveTable(moduleSchema);

            String sql = """
                INSERT INTO %s (
                    event_id, tenant_id, aggregate_type, aggregate_id,
                    event_type, payload, correlation_id, status
                ) VALUES (
                    :eventId, :tenantId, :aggType, :aggId,
                    :eventType, CAST(:payload AS jsonb), :corrId, 'PENDING'
                )
            """.formatted(table);

            String resolvedAggId = aggregateId != null ? aggregateId.toString() : event.aggregateId();

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("eventId", uuidGenerator.generateId())
                    .addValue("tenantId", tenantId)
                    .addValue("aggType", aggregateType != null ? aggregateType : resolveAggregateType(event))
                    .addValue("aggId", resolvedAggId)
                    .addValue("eventType", event.eventType())
                    .addValue("payload", payload)
                    .addValue("corrId", correlationId);

            jdbcTemplate.update(sql, params);
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to persist domain event to outbox", ex);
        }
    }

    private String resolveTable(String moduleSchema) {
        if (moduleSchema == null || moduleSchema.isBlank() || "default".equalsIgnoreCase(moduleSchema.trim())) {
            return "outbox_events";
        }
        return moduleSchema.trim() + ".outbox_events";
    }

    private String resolveAggregateType(DomainEvent event) {
        String simpleName = event.getClass().getSimpleName();
        if (simpleName.endsWith("Event")) {
            simpleName = simpleName.substring(0, simpleName.length() - 5);
        }
        return simpleName.isEmpty() ? "Unknown" : simpleName;
    }

    private UUID resolveAggregateId(DomainEvent event) {
        if (event == null || event.aggregateId() == null) {
            return null;
        }
        try {
            return UUID.fromString(event.aggregateId());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
