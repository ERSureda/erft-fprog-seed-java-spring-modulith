package com.template.api.shared.infrastructure.adapter.out.persistence.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @BeforeEach
    void setUp() {
        idempotencyGate = new JdbcIdempotencyGate(jdbcTemplate);
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
    @DisplayName("tryAcquire with schema parameter should insert and return true when successful")
    void should_return_true_when_try_acquire_with_module_schema_succeeds() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(contains("INSERT INTO shared.processed_events"), eq(Map.of(
                "eventId", eventId,
                "consumerName", "OrderingWorker"
        )))).thenReturn(1);

        boolean acquired = idempotencyGate.tryAcquire("ordering", eventId, "OrderingWorker");

        assertThat(acquired).isTrue();
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

        idempotencyGate.release("ordering", eventId, "OrderingWorker");

        verify(jdbcTemplate).update(
                contains("shared.processed_events WHERE event_id = :eventId AND consumer_name = :consumerName"),
                eq(Map.of("eventId", eventId, "consumerName", "OrderingWorker"))
        );
    }

    @Test
    @DisplayName("isProcessed should return true when count is positive")
    void should_return_true_when_is_processed_count_is_positive() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM shared.processed_events"), anyMap(), eq(Integer.class))).thenReturn(1);

        boolean processed = idempotencyGate.isProcessed(eventId);

        assertThat(processed).isTrue();
    }

    @Test
    @DisplayName("isProcessed with schema should return true when count is positive")
    void should_return_true_when_is_processed_with_schema_count_is_positive() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT(*) FROM shared.processed_events"), eq(Map.of("eventId", eventId)), eq(Integer.class))).thenReturn(1);

        boolean processed = idempotencyGate.isProcessed("ordering", eventId);

        assertThat(processed).isTrue();
    }
}
