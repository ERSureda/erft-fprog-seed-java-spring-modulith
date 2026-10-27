package com.template.api.shared.infrastructure.adapter.out.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.shared.application.port.out.UuidGeneratorPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcOutboxPublisherAdapter Unit Tests")
class JdbcOutboxPublisherAdapterTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UuidGeneratorPort uuidGenerator;

    private JdbcOutboxPublisherAdapter adapter;

    public record SampleOrderCreatedEvent(String aggregateId) implements DomainEvent {}

    @BeforeEach
    void setUp() {
        adapter = new JdbcOutboxPublisherAdapter(jdbcTemplate, objectMapper, uuidGenerator);
    }

    @Test
    @DisplayName("should_publish_single_event_when_valid")
    void should_publish_single_event_when_valid() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(uuidGenerator.generateId()).thenReturn(eventId);

        SampleOrderCreatedEvent event = new SampleOrderCreatedEvent("order-123");
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"orderId\":\"order-123\"}");

        adapter.publish(event);

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO shared.outbox_events"), captor.capture());

        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("eventId")).isEqualTo(eventId);
        assertThat(params.getValue("aggType")).isEqualTo("SampleOrderCreated");
        assertThat(params.getValue("aggId")).isEqualTo("order-123");
        assertThat(params.getValue("eventType")).isEqualTo(SampleOrderCreatedEvent.class.getName());
        assertThat(params.getValue("payload")).isEqualTo("{\"orderId\":\"order-123\"}");
    }

    @Test
    @DisplayName("should_route_to_shared_outbox_in_monolith_mode_and_module_outbox_when_isolated_schemas_enabled")
    void should_publish_canonical_signature_when_schema_and_aggregate_details_provided() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        when(uuidGenerator.generateId()).thenReturn(eventId);

        SampleOrderCreatedEvent event = new SampleOrderCreatedEvent(aggId.toString());
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"orderId\":\"" + aggId + "\"}");

        adapter.publish("ordering", "Order", aggId, event);

        ArgumentCaptor<MapSqlParameterSource> monolithCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO shared.outbox_events"), monolithCaptor.capture());
        assertThat(monolithCaptor.getValue().getValue("aggType")).isEqualTo("Order");
        assertThat(monolithCaptor.getValue().getValue("aggId")).isEqualTo(aggId.toString());

        JdbcOutboxPublisherAdapter isolatedAdapter = new JdbcOutboxPublisherAdapter(jdbcTemplate, objectMapper, uuidGenerator, true);
        isolatedAdapter.publish("ordering", "Order", aggId, event);

        ArgumentCaptor<MapSqlParameterSource> isolatedCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(1)).update(contains("INSERT INTO ordering.outbox_events"), isolatedCaptor.capture());

        MapSqlParameterSource params = isolatedCaptor.getValue();
        assertThat(params.getValue("eventId")).isEqualTo(eventId);
        assertThat(params.getValue("aggType")).isEqualTo("Order");
        assertThat(params.getValue("aggId")).isEqualTo(aggId.toString());
        assertThat(params.getValue("eventType")).isEqualTo(SampleOrderCreatedEvent.class.getName());
        assertThat(params.getValue("payload")).isEqualTo("{\"orderId\":\"" + aggId + "\"}");
    }

    @Test
    @DisplayName("should_batch_insert_events_when_publish_all_receives_multiple_events")
    void should_batch_insert_events_when_publish_all_receives_multiple_events() throws Exception {
        when(uuidGenerator.generateId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        SampleOrderCreatedEvent event1 = new SampleOrderCreatedEvent("order-1");
        SampleOrderCreatedEvent event2 = new SampleOrderCreatedEvent("order-2");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");

        adapter.publishAll(List.of(event1, event2));

        ArgumentCaptor<SqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate, times(1)).batchUpdate(contains("INSERT INTO shared.outbox_events"), batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("should_fallback_to_shared_outbox_when_schema_is_default_or_shared_or_blank")
    void should_fallback_to_shared_outbox_when_schema_is_default_or_shared_or_blank() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggId = UUID.randomUUID();
        when(uuidGenerator.generateId()).thenReturn(eventId);

        SampleOrderCreatedEvent event = new SampleOrderCreatedEvent(aggId.toString());
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"orderId\":\"" + aggId + "\"}");

        JdbcOutboxPublisherAdapter isolatedAdapter = new JdbcOutboxPublisherAdapter(jdbcTemplate, objectMapper, uuidGenerator, true);
        isolatedAdapter.publish("default", "Order", aggId, event);
        isolatedAdapter.publish("shared", "Order", aggId, event);
        isolatedAdapter.publish("  ", "Order", aggId, event);

        verify(jdbcTemplate, times(3)).update(contains("INSERT INTO shared.outbox_events"), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("should_do_nothing_when_event_is_null")
    void should_do_nothing_when_event_is_null() {
        adapter.publish(null);
        adapter.publish("ordering", "Order", UUID.randomUUID(), null);

        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @DisplayName("should_do_nothing_when_events_list_is_null_or_empty")
    void should_do_nothing_when_events_list_is_null_or_empty() {
        adapter.publishAll(null);
        adapter.publishAll(List.of());

        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    @DisplayName("should_throw_infrastructure_exception_when_serialization_fails")
    void should_throw_infrastructure_exception_when_serialization_fails() throws Exception {
        SampleOrderCreatedEvent event = new SampleOrderCreatedEvent("order-123");
        when(objectMapper.writeValueAsString(event)).thenThrow(new JsonProcessingException("Serialization error") {});

        assertThatThrownBy(() -> adapter.publish(event))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("Failed to persist domain event to outbox");
    }

    @Test
    @DisplayName("should_batch_insert_with_schema_and_aggregate_metadata_when_isolated_schemas_enabled")
    void should_batch_insert_with_schema_and_aggregate_metadata_when_isolated_schemas_enabled() throws Exception {
        UUID aggId = UUID.randomUUID();
        when(uuidGenerator.generateId()).thenReturn(UUID.randomUUID(), UUID.randomUUID());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");

        JdbcOutboxPublisherAdapter isolatedAdapter = new JdbcOutboxPublisherAdapter(jdbcTemplate, objectMapper, uuidGenerator, true);
        SampleOrderCreatedEvent event1 = new SampleOrderCreatedEvent(aggId.toString());
        SampleOrderCreatedEvent event2 = new SampleOrderCreatedEvent(aggId.toString());

        isolatedAdapter.publishAll("ordering", "Order", aggId, List.of(event1, event2));

        ArgumentCaptor<SqlParameterSource[]> batchCaptor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate, times(1)).batchUpdate(contains("INSERT INTO ordering.outbox_events"), batchCaptor.capture());
        assertThat(batchCaptor.getValue()).hasSize(2);
        assertThat(batchCaptor.getValue()[0].getValue("aggType")).isEqualTo("Order");
        assertThat(batchCaptor.getValue()[0].getValue("aggId")).isEqualTo(aggId.toString());
    }

    @Test
    @DisplayName("should_throw_validation_exception_when_schema_name_is_invalid")
    void should_throw_validation_exception_when_schema_name_is_invalid() {
        JdbcOutboxPublisherAdapter isolatedAdapter = new JdbcOutboxPublisherAdapter(jdbcTemplate, objectMapper, uuidGenerator, true);
        SampleOrderCreatedEvent event = new SampleOrderCreatedEvent("order-123");

        assertThatThrownBy(() -> isolatedAdapter.publish("invalid-schema!", "Order", UUID.randomUUID(), event))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OUTBOX_INVALID_SCHEMA_NAME");
    }
}
