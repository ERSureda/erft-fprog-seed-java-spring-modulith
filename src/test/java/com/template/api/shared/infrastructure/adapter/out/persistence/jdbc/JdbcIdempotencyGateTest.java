package com.template.api.shared.infrastructure.adapter.out.persistence.jdbc;

import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcIdempotencyGate Unit Tests")
class JdbcIdempotencyGateTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private JdbcIdempotencyGate idempotencyGate;
    private JdbcIdempotencyGate isolatedIdempotencyGate;

    @BeforeEach
    void setUp() {
        idempotencyGate = new JdbcIdempotencyGate(jdbcTemplate);
        isolatedIdempotencyGate = new JdbcIdempotencyGate(jdbcTemplate, true);
    }

    @Test
    @DisplayName("tryAcquire should return true when insert succeeds with default schema")
    void should_return_true_when_try_acquire_succeeds_with_default_schema() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), anyMap())).thenReturn(1);

        boolean acquired = idempotencyGate.tryAcquire(eventId, "TestConsumer");

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("tryAcquire should return false on conflict duplicate")
    void should_return_false_when_try_acquire_detects_conflict() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), anyMap())).thenReturn(0);

        boolean acquired = idempotencyGate.tryAcquire(eventId, "TestConsumer");

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("tryAcquire with schema parameter should use shared in monolith mode and module schema when isolated")
    void should_return_true_when_try_acquire_with_module_schema_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), eq(Map.of(
                "eventId", eventId,
                "consumerName", "OrderingWorker"
        )))).thenReturn(1);
        when(jdbcTemplate.update(contains("INSERT INTO ordering.processed_events"), eq(Map.of(
                "eventId", eventId,
                "consumerName", "OrderingWorker"
        )))).thenReturn(1);

        assertThat(idempotencyGate.tryAcquire("ordering", eventId, "OrderingWorker")).isTrue();
        assertThat(isolatedIdempotencyGate.tryAcquire("ordering", eventId, "OrderingWorker")).isTrue();
    }

    @Test
    @DisplayName("tryAcquire should fallback to default consumer when consumerName is blank or null")
    void should_fallback_to_default_consumer_when_consumer_name_is_blank() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), eq(Map.of(
                "eventId", eventId,
                "consumerName", "default"
        )))).thenReturn(1);

        boolean acquired = idempotencyGate.tryAcquire("ordering", eventId, "  ");

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("tryAcquire should throw NullPointerException when eventId is null")
    void should_throw_when_try_acquire_event_id_is_null() {
        assertThatThrownBy(() -> idempotencyGate.tryAcquire("ordering", null, "OrderingWorker"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("release should execute delete and return true when row removed")
    void should_return_true_when_release_deletes_row() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("DELETE FROM shared.processed_events"), anyMap())).thenReturn(1);

        boolean released = idempotencyGate.release(eventId);

        assertThat(released).isTrue();
        verify(jdbcTemplate).update(contains("DELETE FROM shared.processed_events"), eq(Map.of("eventId", eventId)));
    }

    @Test
    @DisplayName("release with schema and consumerName should execute delete with consumer predicate")
    void should_delete_target_when_release_with_schema_and_consumer_invoked() {
        UUID eventId = UUID.randomUUID();

        isolatedIdempotencyGate.release("ordering", eventId, "OrderingWorker");

        verify(jdbcTemplate).update(
                contains("ordering.processed_events WHERE event_id = :eventId AND consumer_name = :consumerName"),
                eq(Map.of("eventId", eventId, "consumerName", "OrderingWorker"))
        );
    }

    @Test
    @DisplayName("release should execute delete for all consumers when consumerName is null")
    void should_delete_all_consumers_when_release_consumer_name_is_null() {
        UUID eventId = UUID.randomUUID();

        isolatedIdempotencyGate.release("ordering", eventId, null);

        verify(jdbcTemplate).update(
                contains("DELETE FROM ordering.processed_events WHERE event_id = :eventId"),
                eq(Map.of("eventId", eventId))
        );
    }

    @Test
    @DisplayName("release should throw NullPointerException when eventId is null")
    void should_throw_when_release_event_id_is_null() {
        assertThatThrownBy(() -> idempotencyGate.release((UUID) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("isProcessed should return true when event exists")
    void should_return_true_when_is_processed_event_exists() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(contains("EXISTS"), anyMap(), eq(Boolean.class))).thenReturn(Boolean.TRUE);

        boolean processed = idempotencyGate.isProcessed(eventId);

        assertThat(processed).isTrue();
    }

    @Test
    @DisplayName("isProcessed with schema should return true when event exists")
    void should_return_true_when_is_processed_with_schema_event_exists() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(contains("EXISTS"), eq(Map.of("eventId", eventId)), eq(Boolean.class))).thenReturn(Boolean.TRUE);

        boolean processed = isolatedIdempotencyGate.isProcessed("ordering", eventId);

        assertThat(processed).isTrue();
    }

    @Test
    @DisplayName("isProcessed should return false when event does not exist")
    void should_return_false_when_is_processed_event_not_exists() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(contains("EXISTS"), eq(Map.of("eventId", eventId)), eq(Boolean.class))).thenReturn(Boolean.FALSE);

        boolean processed = idempotencyGate.isProcessed(eventId);

        assertThat(processed).isFalse();
    }

    @Test
    @DisplayName("isProcessed should throw NullPointerException when eventId is null")
    void should_throw_when_is_processed_event_id_is_null() {
        assertThatThrownBy(() -> idempotencyGate.isProcessed(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("IDEMPOTENCY_EVENT_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("tryAcquire should fallback to shared table when schema is default or shared or blank")
    void should_fallback_to_shared_table_when_schema_is_default_or_shared() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), anyMap())).thenReturn(1);

        assertThat(isolatedIdempotencyGate.tryAcquire("default", eventId, "Worker")).isTrue();
        assertThat(isolatedIdempotencyGate.tryAcquire("shared", eventId, "Worker")).isTrue();
        assertThat(isolatedIdempotencyGate.tryAcquire("   ", eventId, "Worker")).isTrue();
        assertThat(isolatedIdempotencyGate.tryAcquire(null, eventId, "Worker")).isTrue();
    }

    @Test
    @DisplayName("should_throw_validation_exception_when_schema_name_is_invalid")
    void should_throw_validation_exception_when_schema_name_is_invalid() {
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> isolatedIdempotencyGate.tryAcquire("bad-schema;DROP", eventId, "Worker"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("IDEMPOTENCY_INVALID_SCHEMA_NAME");
    }

    @Test
    @DisplayName("should_wrap_jdbc_exceptions_in_infrastructure_exception_preserving_cause")
    void should_wrap_jdbc_exceptions_in_infrastructure_exception_preserving_cause() {
        UUID eventId = UUID.randomUUID();
        DataAccessResourceFailureException dbError = new DataAccessResourceFailureException("DB connection lost");
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), anyMap())).thenThrow(dbError);

        assertThatThrownBy(() -> idempotencyGate.tryAcquire(eventId, "Worker"))
                .isInstanceOf(InfrastructureException.class)
                .hasCause(dbError);
    }
}
