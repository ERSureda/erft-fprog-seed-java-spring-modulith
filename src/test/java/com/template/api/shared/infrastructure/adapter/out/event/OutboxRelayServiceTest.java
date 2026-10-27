package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.EventPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayService Unit Tests")
class OutboxRelayServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private EventPublisherPort eventPublisherPort;

    private ObjectMapper objectMapper;
    private OutboxRelayService service;

    public record TestOutboxEvent(String aggregateId) implements DomainEvent {}

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new OutboxRelayService(jdbcTemplate, eventPublisherPort, objectMapper);
    }

    @Test
    @DisplayName("Should do nothing when no pending outbox events exist")
    void should_do_nothing_when_no_pending_outbox_events_exist() {
        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of());

        service.processPendingEvents();

        verify(eventPublisherPort, never()).publish(any());
        verify(jdbcTemplate, never()).update(anyString(), anyMap());
    }

    @Test
    @DisplayName("Should claim in batch, propagate context, publish domain event, and transition to DELIVERED")
    void should_transition_status_and_publish_event_when_pending_events_exist() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        TestOutboxEvent event = new TestOutboxEvent("agg-1");
        String payloadJson = objectMapper.writeValueAsString(event);

        Map<String, Object> row = new HashMap<>();
        row.put("event_id", eventId);
        row.put("tenant_id", tenantId);
        row.put("correlation_id", "corr-relay-1");
        row.put("event_type", TestOutboxEvent.class.getName());
        row.put("payload", payloadJson);
        row.put("retry_count", 0);

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row));

        AtomicReference<ExecutionContext> capturedContext = new AtomicReference<>();
        AtomicReference<UUID> capturedEventId = new AtomicReference<>();
        doAnswer(inv -> {
            capturedContext.set(ExecutionContextHolder.get());
            capturedEventId.set(ExecutionContextHolder.getEventId());
            return null;
        }).when(eventPublisherPort).publish(any());

        service.processPendingEvents();

        verify(jdbcTemplate).queryForList(contains("SET status = 'PROCESSING'"), eq(Map.of("limit", 50, "staleMinutes", 5)));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aggregateId()).isEqualTo("agg-1");
        assertThat(capturedContext.get()).isNotNull();
        assertThat(capturedContext.get().tenantId()).isEqualTo(tenantId);
        assertThat(capturedContext.get().correlationId()).isEqualTo("corr-relay-1");
        assertThat(capturedEventId.get()).isEqualTo(eventId);
        assertThat(ExecutionContextHolder.getEventId()).isNull();

        verify(jdbcTemplate).update(contains("status = 'DELIVERED'"), eq(Map.of("ids", List.of(eventId))));
    }

    @Test
    @DisplayName("Should claim multiple events in a single batch and settle delivered IDs in batch")
    void should_claim_multiple_events_and_settle_each_when_batch_processed() throws Exception {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        TestOutboxEvent event1 = new TestOutboxEvent("agg-1");
        TestOutboxEvent event2 = new TestOutboxEvent("agg-2");

        Map<String, Object> row1 = Map.of(
                "event_id", eventId1,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", objectMapper.writeValueAsString(event1),
                "retry_count", 0
        );
        Map<String, Object> row2 = Map.of(
                "event_id", eventId2,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", objectMapper.writeValueAsString(event2),
                "retry_count", 0
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row1, row2));

        service.processPendingEvents();

        verify(jdbcTemplate).queryForList(contains("SET status = 'PROCESSING'"), eq(Map.of("limit", 50, "staleMinutes", 5)));
        verify(eventPublisherPort, times(2)).publish(any(DomainEvent.class));
        verify(jdbcTemplate).update(contains("status = 'DELIVERED'"), eq(Map.of("ids", List.of(eventId1, eventId2))));
    }

    @Test
    @DisplayName("Should mark FAILED with exponential backoff when event publication fails")
    void should_mark_failed_with_exponential_backoff_when_publishing_throws_exception() throws Exception {
        UUID eventId = UUID.randomUUID();
        TestOutboxEvent event = new TestOutboxEvent("agg-fail");
        String payloadJson = objectMapper.writeValueAsString(event);

        Map<String, Object> row = Map.of(
                "event_id", eventId,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", payloadJson,
                "retry_count", 1
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row));
        doThrow(new RuntimeException("External broker unreachable")).when(eventPublisherPort).publish(any());

        service.processPendingEvents();

        verify(jdbcTemplate).queryForList(contains("SET status = 'PROCESSING'"), eq(Map.of("limit", 50, "staleMinutes", 5)));
        verify(jdbcTemplate, never()).update(contains("status = 'DELIVERED'"), anyMap());

        verify(jdbcTemplate).update(
                contains("SET status = CASE WHEN :nextRetry >= :maxRetries THEN 'DEAD_LETTER' ELSE 'FAILED' END"),
                eq(Map.of(
                        "id", eventId,
                        "nextRetry", 2,
                        "maxRetries", 5,
                        "error", "External broker unreachable",
                        "delay", 4L
                ))
        );
    }

    @Test
    @DisplayName("Should route to DEAD_LETTER when retry limit is reached")
    void should_transition_to_dead_letter_when_retry_limit_reached() throws Exception {
        UUID eventId = UUID.randomUUID();
        TestOutboxEvent event = new TestOutboxEvent("agg-dlq");
        String payloadJson = objectMapper.writeValueAsString(event);

        Map<String, Object> row = Map.of(
                "event_id", eventId,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", payloadJson,
                "retry_count", 4
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row));
        doThrow(new RuntimeException("Terminal failure")).when(eventPublisherPort).publish(any());

        service.processPendingEvents();

        verify(jdbcTemplate).update(
                contains("SET status = CASE WHEN :nextRetry >= :maxRetries THEN 'DEAD_LETTER' ELSE 'FAILED' END"),
                eq(Map.of(
                        "id", eventId,
                        "nextRetry", 5,
                        "maxRetries", 5,
                        "error", "Terminal failure",
                        "delay", 32L
                ))
        );
    }

    @Test
    @DisplayName("Should isolate event settlements so failure of one event does not affect another")
    void should_handle_partial_batch_failure_without_affecting_other_events_when_one_event_fails() throws Exception {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        TestOutboxEvent event1 = new TestOutboxEvent("agg-ok");
        TestOutboxEvent event2 = new TestOutboxEvent("agg-fail");

        Map<String, Object> row1 = Map.of(
                "event_id", eventId1,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", objectMapper.writeValueAsString(event1),
                "retry_count", 0
        );
        Map<String, Object> row2 = Map.of(
                "event_id", eventId2,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", objectMapper.writeValueAsString(event2),
                "retry_count", 0
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row1, row2));
        doAnswer(invocation -> {
            DomainEvent arg = invocation.getArgument(0);
            if ("agg-fail".equals(arg.aggregateId())) {
                throw new RuntimeException("Network glitch");
            }
            return null;
        }).when(eventPublisherPort).publish(any());

        service.processPendingEvents();

        verify(jdbcTemplate).update(contains("status = 'DELIVERED'"), eq(Map.of("ids", List.of(eventId1))));
        verify(jdbcTemplate).update(
                contains("SET status = CASE WHEN :nextRetry >= :maxRetries THEN 'DEAD_LETTER' ELSE 'FAILED' END"),
                eq(Map.of(
                        "id", eventId2,
                        "nextRetry", 1,
                        "maxRetries", 5,
                        "error", "Network glitch",
                        "delay", 2L
                ))
        );
    }

    @Test
    @DisplayName("Should mark FAILED when event class cannot be resolved")
    void should_mark_failed_when_event_class_resolution_fails() {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> row = Map.of(
                "event_id", eventId,
                "event_type", "com.template.unknown.NonExistentEvent",
                "payload", "{}",
                "retry_count", 0
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row));

        service.processPendingEvents();

        verify(eventPublisherPort, never()).publish(any());
        verify(jdbcTemplate).update(
                contains("SET status = CASE WHEN :nextRetry >= :maxRetries THEN 'DEAD_LETTER' ELSE 'FAILED' END"),
                eq(Map.of(
                        "id", eventId,
                        "nextRetry", 1,
                        "maxRetries", 5,
                        "error", "com.template.unknown.NonExistentEvent",
                        "delay", 2L
                ))
        );
    }

    @Test
    @DisplayName("Should handle database failure during claim phase gracefully without throwing")
    void should_handle_failure_gracefully_when_database_fails_during_claim_phase() {
        when(jdbcTemplate.queryForList(anyString(), anyMap()))
                .thenThrow(new DataAccessResourceFailureException("Database unreachable"));

        service.processPendingEvents();

        verify(eventPublisherPort, never()).publish(any());
        verify(jdbcTemplate, never()).update(contains("status = 'PROCESSING'"), anyMap());
    }

    @Test
    @DisplayName("Should execute scheduled purge of delivered outbox events and processed idempotency events")
    void should_execute_delete_when_purging_delivered_events() {
        when(jdbcTemplate.update(contains("DELETE FROM shared.outbox_events"), anyMap())).thenReturn(5);
        when(jdbcTemplate.update(contains("DELETE FROM shared.processed_events"), anyMap())).thenReturn(3);

        service.purgeDeliveredEvents();

        verify(jdbcTemplate).update(contains("DELETE FROM shared.outbox_events"), eq(Map.of("days", 7)));
        verify(jdbcTemplate).update(contains("DELETE FROM shared.processed_events"), eq(Map.of("days", 7)));
    }
}
