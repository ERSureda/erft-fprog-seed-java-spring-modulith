package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.OutboxPublisherPort;
import com.template.api.shared.application.port.out.UuidGeneratorPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.domain.exception.BaseException;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ValidationException;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class JdbcOutboxPublisherAdapter implements OutboxPublisherPort {

    private static final String DEFAULT_TABLE = "shared.outbox_events";
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private static final String SQL_INSERT_TEMPLATE = """
            INSERT INTO %s (
                event_id, tenant_id, aggregate_type, aggregate_id,
                event_type, payload, correlation_id, status
            ) VALUES (
                :eventId, :tenantId, :aggType, :aggId,
                :eventType, CAST(:payload AS jsonb), :corrId, 'PENDING'
            )
            """;

    private static final String SQL_INSERT_SHARED = SQL_INSERT_TEMPLATE.formatted(DEFAULT_TABLE);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UuidGeneratorPort uuidGenerator;
    private final boolean isolatedSchemas;

    @Autowired
    public JdbcOutboxPublisherAdapter(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            UuidGeneratorPort uuidGenerator,
            @Value("${outbox.isolated-schemas:false}") boolean isolatedSchemas
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uuidGenerator = uuidGenerator;
        this.isolatedSchemas = isolatedSchemas;
    }

    public JdbcOutboxPublisherAdapter(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            UuidGeneratorPort uuidGenerator
    ) {
        this(jdbcTemplate, objectMapper, uuidGenerator, false);
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        publish(null, resolveAggregateType(event), resolveAggregateId(event), event);
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        publishAll(null, null, null, events);
    }

    @Override
    public void publishAll(String moduleSchema, String aggregateType, UUID aggregateId, List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<DomainEvent> validEvents = new ArrayList<>(events.size());
        for (DomainEvent event : events) {
            if (event != null) {
                validEvents.add(event);
            }
        }
        if (validEvents.isEmpty()) {
            return;
        }
        if (validEvents.size() == 1) {
            publish(moduleSchema, aggregateType, aggregateId, validEvents.getFirst());
            return;
        }

        try {
            ExecutionContext ctx = ExecutionContextHolder.get();
            String correlationId = ctx != null ? ctx.correlationId() : null;
            UUID tenantId = ctx != null ? ctx.tenantId() : null;

            String table = resolveTable(moduleSchema);
            String sql = DEFAULT_TABLE.equals(table) ? SQL_INSERT_SHARED : SQL_INSERT_TEMPLATE.formatted(table);
            String explicitAggId = aggregateId != null ? aggregateId.toString() : null;

            SqlParameterSource[] batchParams = new SqlParameterSource[validEvents.size()];
            for (int i = 0; i < validEvents.size(); i++) {
                DomainEvent event = validEvents.get(i);
                String resolvedAggType = aggregateType != null ? aggregateType : resolveAggregateType(event);
                String resolvedAggId = explicitAggId != null ? explicitAggId : event.aggregateId();
                batchParams[i] = buildParameterSource(
                        tenantId,
                        correlationId,
                        resolvedAggType,
                        resolvedAggId,
                        event
                );
            }
            jdbcTemplate.batchUpdate(sql, batchParams);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to persist domain events batch to outbox", ex);
        }
    }

    @Override
    public void publish(String moduleSchema, String aggregateType, UUID aggregateId, DomainEvent event) {
        if (event == null) {
            return;
        }
        try {
            ExecutionContext ctx = ExecutionContextHolder.get();
            String correlationId = ctx != null ? ctx.correlationId() : null;
            UUID tenantId = ctx != null ? ctx.tenantId() : null;

            String table = resolveTable(moduleSchema);
            String sql = DEFAULT_TABLE.equals(table) ? SQL_INSERT_SHARED : SQL_INSERT_TEMPLATE.formatted(table);

            String resolvedAggType = aggregateType != null ? aggregateType : resolveAggregateType(event);
            String resolvedAggId = aggregateId != null ? aggregateId.toString() : event.aggregateId();

            MapSqlParameterSource params = buildParameterSource(
                    tenantId,
                    correlationId,
                    resolvedAggType,
                    resolvedAggId,
                    event
            );

            jdbcTemplate.update(sql, params);
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InfrastructureException("Failed to persist domain event to outbox", ex);
        }
    }

    private MapSqlParameterSource buildParameterSource(
            UUID tenantId,
            String correlationId,
            String aggregateType,
            String aggregateId,
            DomainEvent event
    ) throws Exception {
        String payload = objectMapper.writeValueAsString(event);
        return new MapSqlParameterSource()
                .addValue("eventId", uuidGenerator.generateId())
                .addValue("tenantId", tenantId)
                .addValue("aggType", aggregateType)
                .addValue("aggId", aggregateId)
                .addValue("eventType", event.eventType())
                .addValue("payload", payload)
                .addValue("corrId", correlationId);
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
            throw new ValidationException("OUTBOX_INVALID_SCHEMA_NAME: " + trimmed);
        }
        return trimmed + ".outbox_events";
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
