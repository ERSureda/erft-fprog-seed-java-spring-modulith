package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.port.out.EventPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    }

    @Test
    @DisplayName("Should transition to PROCESSING, publish domain event, and transition to DELIVERED")
    void should_transition_status_and_publish_event_when_pending_events_exist() throws Exception {
        UUID eventId = UUID.randomUUID();
        TestOutboxEvent event = new TestOutboxEvent("agg-1");
        String payloadJson = objectMapper.writeValueAsString(event);

        Map<String, Object> row = Map.of(
                "event_id", eventId,
                "event_type", TestOutboxEvent.class.getName(),
                "payload", payloadJson,
                "retry_count", 0
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(List.of(row));

        service.processPendingEvents();

        verify(jdbcTemplate).update(contains("status = 'PROCESSING'"), eq(Map.of("id", eventId)));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisherPort).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().aggregateId()).isEqualTo("agg-1");

        verify(jdbcTemplate).update(contains("status = 'DELIVERED'"), eq(Map.of("id", eventId)));
    }

    @Test
    @DisplayName("Should execute scheduled purge of delivered outbox events and processed idempotency events")
    void should_execute_delete_when_purging_delivered_events() {
        when(jdbcTemplate.update(contains("DELETE FROM outbox_events"), anyMap())).thenReturn(5);
        when(jdbcTemplate.update(contains("DELETE FROM processed_events"), anyMap())).thenReturn(3);

        service.purgeDeliveredEvents();

        verify(jdbcTemplate).update(contains("DELETE FROM outbox_events"), eq(Map.of("days", 7)));
        verify(jdbcTemplate).update(contains("DELETE FROM processed_events"), eq(Map.of("days", 7)));
    }
}
