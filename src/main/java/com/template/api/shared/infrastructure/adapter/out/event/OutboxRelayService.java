package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.port.out.EventPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnBean(NamedParameterJdbcTemplate.class)
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final Map<String, Class<?>> EVENT_CLASS_CACHE = new ConcurrentHashMap<>(16);

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;
    private static final int STALE_LOCK_MINUTES = 5;
    private static final int DEFAULT_RETENTION_DAYS = 7;
    private static final long MAX_BACKOFF_SECONDS = 3600L;

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
        this.schemas = (schemas == null || schemas.isEmpty()) ? List.of("") : List.copyOf(schemas);
    }

    public OutboxRelayService(
            NamedParameterJdbcTemplate jdbcTemplate,
            EventPublisherPort eventPublisherPort,
            ObjectMapper objectMapper,
            TransactionOperations transactionOperations
    ) {
        this(jdbcTemplate, eventPublisherPort, objectMapper, transactionOperations, List.of(""));
    }

    public OutboxRelayService(
            NamedParameterJdbcTemplate jdbcTemplate,
            EventPublisherPort eventPublisherPort,
            ObjectMapper objectMapper
    ) {
        this(jdbcTemplate, eventPublisherPort, objectMapper, TransactionOperations.withoutTransaction(), List.of(""));
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

        for (OutboxRecord record : claimedEvents) {
            publishAndSettle(outboxTable, record);
        }
    }

    private List<OutboxRecord> claimPendingEvents(String outboxTable) {
        List<OutboxRecord> claimed = transactionOperations.execute(_ -> {
            String selectSql = """
                SELECT event_id, event_type, payload, retry_count
                FROM %s
                WHERE status = 'PENDING'
                   OR (status = 'FAILED' AND next_retry_at <= NOW())
                   OR (status = 'PROCESSING' AND locked_at < NOW() - INTERVAL '1 minute' * :staleMinutes)
                ORDER BY created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            """.formatted(outboxTable);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    selectSql,
                    Map.of("limit", BATCH_SIZE, "staleMinutes", STALE_LOCK_MINUTES)
            );

            if (rows.isEmpty()) {
                return List.of();
            }

            List<UUID> ids = new ArrayList<>(rows.size());
            List<OutboxRecord> records = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                UUID eventId = (UUID) row.get("event_id");
                String eventType = (String) row.get("event_type");
                String payloadJson = String.valueOf(row.get("payload"));
                int retryCount = ((Number) row.get("retry_count")).intValue();
                ids.add(eventId);
                records.add(new OutboxRecord(eventId, eventType, payloadJson, retryCount));
            }

            String updateSql = "UPDATE " + outboxTable + " SET status = 'PROCESSING', locked_at = NOW() WHERE event_id IN (:ids)";
            jdbcTemplate.update(updateSql, Map.of("ids", ids));

            return records;
        });

        return claimed != null ? claimed : List.of();
    }

    private void publishAndSettle(String outboxTable, OutboxRecord record) {
        try {
            Class<?> eventClass = resolveEventClass(record.eventType());
            DomainEvent domainEvent = (DomainEvent) objectMapper.readValue(record.payloadJson(), eventClass);
            eventPublisherPort.publish(domainEvent);
            markDelivered(outboxTable, record.eventId());
        } catch (Exception ex) {
            log.error("Failed to relay outbox event [id={}]", record.eventId(), ex);
            markFailed(outboxTable, record.eventId(), record.retryCount(), ex);
        }
    }

    private void markDelivered(String outboxTable, UUID eventId) {
        String updateSql = """
            UPDATE %s
            SET status = 'DELIVERED',
                delivered_at = NOW(),
                locked_at = NULL,
                next_retry_at = NULL
            WHERE event_id = :id
        """.formatted(outboxTable);
        jdbcTemplate.update(updateSql, Map.of("id", eventId));
    }

    private void markFailed(String outboxTable, UUID eventId, int retryCount, Exception ex) {
        try {
            int nextRetry = retryCount + 1;
            long delaySeconds = (nextRetry >= 30) ? MAX_BACKOFF_SECONDS : Math.min((long) Math.pow(2, nextRetry), MAX_BACKOFF_SECONDS);
            String rawMessage = ex.getMessage();
            String message = (rawMessage != null && !rawMessage.isBlank()) ? rawMessage : ex.getClass().getName();
            String errorMessage = message.length() > 4000 ? message.substring(0, 4000) : message;

            String updateSql = """
                UPDATE %s
                SET status = CASE WHEN :nextRetry >= :maxRetries THEN 'DEAD_LETTER' ELSE 'FAILED' END,
                    retry_count = :nextRetry,
                    last_error = :error,
                    locked_at = NULL,
                    next_retry_at = CASE WHEN :nextRetry >= :maxRetries THEN NULL ELSE NOW() + INTERVAL '1 second' * :delay END
                WHERE event_id = :id
            """.formatted(outboxTable);

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
                String deleteSql = """
                    DELETE FROM %s
                    WHERE status = 'DELIVERED'
                      AND created_at < NOW() - INTERVAL '1 day' * :days
                """.formatted(outboxTable);
                int purgedRows = jdbcTemplate.update(deleteSql, Map.of("days", DEFAULT_RETENTION_DAYS));
                if (purgedRows > 0) {
                    log.info("Purged {} delivered outbox events older than {} days from {}", purgedRows, DEFAULT_RETENTION_DAYS, outboxTable);
                }

                String deleteProcessedSql = """
                    DELETE FROM %s
                    WHERE processed_at < NOW() - INTERVAL '1 day' * :days
                """.formatted(processedTable);
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
            return tableName;
        }
        return schema.trim() + "." + tableName;
    }

    private static Class<?> resolveEventClass(String eventType) throws ClassNotFoundException {
        Class<?> cached = EVENT_CLASS_CACHE.get(eventType);
        if (cached != null) {
            return cached;
        }
        Class<?> loaded = Class.forName(eventType);
        EVENT_CLASS_CACHE.put(eventType, loaded);
        return loaded;
    }

    private record OutboxRecord(UUID eventId, String eventType, String payloadJson, int retryCount) {}
}
