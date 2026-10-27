package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.EventPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.domain.exception.ValidationException;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
@ConditionalOnBean(NamedParameterJdbcTemplate.class)
public class OutboxRelayService {

    private static final Map<String, Class<? extends DomainEvent>> EVENT_CLASS_CACHE = new ConcurrentHashMap<>(16);
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final String DEFAULT_OUTBOX_TABLE = "shared.outbox_events";
    private static final String DEFAULT_PROCESSED_TABLE = "shared.processed_events";
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_EVENT_ID = "eventId";

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;
    private static final int STALE_LOCK_MINUTES = 5;
    private static final int DEFAULT_RETENTION_DAYS = 7;
    private static final long MAX_BACKOFF_SECONDS = 3600L;

    private static final String SQL_CLAIM_AND_LOCK_TEMPLATE = """
        WITH claimed AS (
            SELECT event_id
            FROM %1$s
            WHERE status = 'PENDING'
               OR (status = 'FAILED' AND next_retry_at <= NOW())
               OR (status = 'PROCESSING' AND locked_at < NOW() - INTERVAL '1 minute' * :staleMinutes)
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        UPDATE %1$s o
        SET status = 'PROCESSING',
            locked_at = NOW()
        FROM claimed c
        WHERE o.event_id = c.event_id
        RETURNING o.event_id, o.tenant_id, o.correlation_id, o.event_type, o.payload, o.retry_count
        """;
    private static final String SQL_CLAIM_AND_LOCK_SHARED = SQL_CLAIM_AND_LOCK_TEMPLATE.formatted(DEFAULT_OUTBOX_TABLE);

    private static final String SQL_UPDATE_DELIVERED_TEMPLATE = """
        UPDATE %s
        SET status = 'DELIVERED',
            delivered_at = NOW(),
            locked_at = NULL,
            next_retry_at = NULL
        WHERE event_id IN (:ids)
        """;
    private static final String SQL_UPDATE_DELIVERED_SHARED = SQL_UPDATE_DELIVERED_TEMPLATE.formatted(DEFAULT_OUTBOX_TABLE);

    private static final String SQL_UPDATE_FAILED_TEMPLATE = """
        UPDATE %s
        SET status = CASE WHEN :nextRetry >= :maxRetries THEN 'DEAD_LETTER' ELSE 'FAILED' END,
            retry_count = :nextRetry,
            last_error = :error,
            locked_at = NULL,
            next_retry_at = CASE WHEN :nextRetry >= :maxRetries THEN NULL ELSE NOW() + INTERVAL '1 second' * :delay END
        WHERE event_id = :id
        """;
    private static final String SQL_UPDATE_FAILED_SHARED = SQL_UPDATE_FAILED_TEMPLATE.formatted(DEFAULT_OUTBOX_TABLE);

    private static final String SQL_PURGE_OUTBOX_TEMPLATE = """
        DELETE FROM %s
        WHERE status = 'DELIVERED'
          AND delivered_at < NOW() - INTERVAL '1 day' * :days
        """;
    private static final String SQL_PURGE_OUTBOX_SHARED = SQL_PURGE_OUTBOX_TEMPLATE.formatted(DEFAULT_OUTBOX_TABLE);

    private static final String SQL_PURGE_PROCESSED_TEMPLATE = """
        DELETE FROM %s
        WHERE processed_at < NOW() - INTERVAL '1 day' * :days
        """;
    private static final String SQL_PURGE_PROCESSED_SHARED = SQL_PURGE_PROCESSED_TEMPLATE.formatted(DEFAULT_PROCESSED_TABLE);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EventPublisherPort eventPublisherPort;
    private final ObjectMapper objectMapper;
    private final TransactionOperations transactionOperations;
    private final List<String> schemas;

    @Autowired
    public OutboxRelayService(
            NamedParameterJdbcTemplate jdbcTemplate,
            EventPublisherPort eventPublisherPort,
            ObjectMapper objectMapper,
            @Autowired(required = false) TransactionOperations transactionOperations,
            @Value("${outbox.schemas:shared}") List<String> schemas
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventPublisherPort = eventPublisherPort;
        this.objectMapper = objectMapper;
        this.transactionOperations = transactionOperations != null
                ? transactionOperations
                : TransactionOperations.withoutTransaction();
        this.schemas = (schemas == null || schemas.isEmpty()) ? List.of("shared") : List.copyOf(schemas);
    }

    public OutboxRelayService(
            NamedParameterJdbcTemplate jdbcTemplate,
            EventPublisherPort eventPublisherPort,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations
    ) {
        this(jdbcTemplate, eventPublisherPort, objectMapper, transactionOperations, List.of("shared"));
    }

    public OutboxRelayService(
            NamedParameterJdbcTemplate jdbcTemplate,
            EventPublisherPort eventPublisherPort,
            ObjectMapper objectMapper
    ) {
        this(jdbcTemplate, eventPublisherPort, objectMapper, TransactionOperations.withoutTransaction(), List.of("shared"));
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:1000}")
    public void processPendingEvents() {
        for (String schema : schemas) {
            processPendingEventsForSchema(schema);
        }
    }

    private void processPendingEventsForSchema(String schema) {
        String outboxTable = qualifyTable(schema, "outbox_events");
        List<OutboxRecord> claimedEvents;
        try {
            claimedEvents = claimPendingEvents(outboxTable);
        } catch (Exception ex) {
            log.error("Failed to claim outbox events from table [{}]", outboxTable, ex);
            return;
        }

        if (claimedEvents == null || claimedEvents.isEmpty()) {
            return;
        }

        List<UUID> deliveredIds = new ArrayList<>(claimedEvents.size());
        for (OutboxRecord record : claimedEvents) {
            if (publishEvent(outboxTable, record)) {
                deliveredIds.add(record.eventId());
            }
        }

        if (!deliveredIds.isEmpty()) {
            markDeliveredBatch(outboxTable, deliveredIds);
        }
    }

    private List<OutboxRecord> claimPendingEvents(String outboxTable) {
        List<OutboxRecord> claimed = transactionOperations.execute(_ -> {
            String claimSql = DEFAULT_OUTBOX_TABLE.equals(outboxTable)
                    ? SQL_CLAIM_AND_LOCK_SHARED
                    : SQL_CLAIM_AND_LOCK_TEMPLATE.formatted(outboxTable);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    claimSql,
                    Map.of("limit", BATCH_SIZE, "staleMinutes", STALE_LOCK_MINUTES)
            );

            if (rows.isEmpty()) {
                return List.of();
            }

            List<OutboxRecord> records = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                UUID eventId = (UUID) row.get("event_id");
                UUID tenantId = (UUID) row.get("tenant_id");
                String correlationId = (String) row.get("correlation_id");
                String eventType = (String) row.get("event_type");
                String payloadJson = String.valueOf(row.get("payload"));
                int retryCount = ((Number) row.get("retry_count")).intValue();
                records.add(new OutboxRecord(eventId, tenantId, correlationId, eventType, payloadJson, retryCount));
            }

            return records;
        });

        return claimed != null ? claimed : List.of();
    }

    private boolean publishEvent(String outboxTable, OutboxRecord record) {
        try {
            Class<? extends DomainEvent> eventClass = resolveEventClass(record.eventType());
            DomainEvent domainEvent = objectMapper.readValue(record.payloadJson(), eventClass);
            dispatchWithContext(record, domainEvent);
            return true;
        } catch (Exception ex) {
            log.error("Failed to relay outbox event [id={}]", record.eventId(), ex);
            markFailed(outboxTable, record.eventId(), record.retryCount(), ex);
            return false;
        }
    }

    private void dispatchWithContext(OutboxRecord record, DomainEvent domainEvent) {
        String correlationId = (record.correlationId() != null && !record.correlationId().isBlank())
                ? record.correlationId()
                : record.eventId().toString();
        ExecutionContext relayContext = new ExecutionContext(
                record.tenantId(),
                null,
                Set.of("SYSTEM"),
                correlationId
        );
        ExecutionContextHolder.runWith(relayContext, record.eventId(), () -> {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            if (record.eventId() != null) {
                MDC.put(MDC_EVENT_ID, record.eventId().toString());
            }
            if (record.tenantId() != null) {
                MDC.put(MDC_TENANT_ID, record.tenantId().toString());
            }
            try {
                eventPublisherPort.publish(domainEvent);
            } finally {
                MDC.remove(MDC_CORRELATION_ID);
                MDC.remove(MDC_EVENT_ID);
                MDC.remove(MDC_TENANT_ID);
            }
        });
    }

    private void markDeliveredBatch(String outboxTable, List<UUID> eventIds) {
        String updateSql = DEFAULT_OUTBOX_TABLE.equals(outboxTable)
                ? SQL_UPDATE_DELIVERED_SHARED
                : SQL_UPDATE_DELIVERED_TEMPLATE.formatted(outboxTable);
        jdbcTemplate.update(updateSql, Map.of("ids", eventIds));
    }

    private void markFailed(String outboxTable, UUID eventId, int retryCount, Exception ex) {
        try {
            int nextRetry = retryCount + 1;
            long delaySeconds = (nextRetry >= 30) ? MAX_BACKOFF_SECONDS : Math.min(1L << nextRetry, MAX_BACKOFF_SECONDS);
            String rawMessage = ex.getMessage();
            String message = (rawMessage != null && !rawMessage.isBlank()) ? rawMessage : ex.getClass().getName();
            String errorMessage = message.length() > 4000 ? message.substring(0, 4000) : message;

            String updateSql = DEFAULT_OUTBOX_TABLE.equals(outboxTable)
                    ? SQL_UPDATE_FAILED_SHARED
                    : SQL_UPDATE_FAILED_TEMPLATE.formatted(outboxTable);

            jdbcTemplate.update(updateSql, Map.of(
                    "id", eventId,
                    "nextRetry", nextRetry,
                    "maxRetries", MAX_RETRIES,
                    "error", errorMessage,
                    "delay", delaySeconds
            ));
        } catch (Exception dbEx) {
            log.error("Failed to update failure state for outbox event [id={}]", eventId, dbEx);
        }
    }

    @Scheduled(cron = "${outbox.purge-cron:0 0 3 * * ?}")
    public void purgeDeliveredEvents() {
        for (String schema : schemas) {
            purgeDeliveredEventsForSchema(schema);
        }
    }

    private void purgeDeliveredEventsForSchema(String schema) {
        String outboxTable = qualifyTable(schema, "outbox_events");
        String processedTable = qualifyTable(schema, "processed_events");
        try {
            transactionOperations.executeWithoutResult(_ -> {
                String deleteSql = DEFAULT_OUTBOX_TABLE.equals(outboxTable)
                        ? SQL_PURGE_OUTBOX_SHARED
                        : SQL_PURGE_OUTBOX_TEMPLATE.formatted(outboxTable);
                int purgedRows = jdbcTemplate.update(deleteSql, Map.of("days", DEFAULT_RETENTION_DAYS));
                if (purgedRows > 0) {
                    log.info("Purged {} delivered outbox events older than {} days from {}", purgedRows, DEFAULT_RETENTION_DAYS, outboxTable);
                }

                String deleteProcessedSql = DEFAULT_PROCESSED_TABLE.equals(processedTable)
                        ? SQL_PURGE_PROCESSED_SHARED
                        : SQL_PURGE_PROCESSED_TEMPLATE.formatted(processedTable);
                int purgedProcessedRows = jdbcTemplate.update(deleteProcessedSql, Map.of("days", DEFAULT_RETENTION_DAYS));
                if (purgedProcessedRows > 0) {
                    log.info("Purged {} processed idempotency events older than {} days from {}", purgedProcessedRows, DEFAULT_RETENTION_DAYS, processedTable);
                }
            });
        } catch (Exception ex) {
            log.error("Failed to purge delivered events for schema [{}]", schema, ex);
        }
    }

    private static String qualifyTable(String schema, String tableName) {
        if (schema == null || schema.isBlank() || "public".equalsIgnoreCase(schema.trim()) || "default".equalsIgnoreCase(schema.trim())) {
            return "shared." + tableName;
        }
        String trimmed = schema.trim();
        if (!SCHEMA_PATTERN.matcher(trimmed).matches()) {
            throw new ValidationException("OUTBOX_INVALID_SCHEMA_NAME: " + trimmed);
        }
        return trimmed + "." + tableName;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends DomainEvent> resolveEventClass(String eventType) throws ClassNotFoundException {
        Class<? extends DomainEvent> cached = EVENT_CLASS_CACHE.get(eventType);
        if (cached != null) {
            return cached;
        }
        Class<?> loaded = Class.forName(eventType);
        if (!DomainEvent.class.isAssignableFrom(loaded)) {
            throw new ValidationException("OUTBOX_INVALID_EVENT_CLASS: " + eventType);
        }
        Class<? extends DomainEvent> domainEventClass = (Class<? extends DomainEvent>) loaded;
        EVENT_CLASS_CACHE.put(eventType, domainEventClass);
        return domainEventClass;
    }

    private record OutboxRecord(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String eventType,
            String payloadJson,
            int retryCount
    ) {}
}
