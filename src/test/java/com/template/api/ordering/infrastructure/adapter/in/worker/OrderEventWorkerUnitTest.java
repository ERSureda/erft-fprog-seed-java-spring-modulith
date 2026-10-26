package com.template.api.ordering.infrastructure.adapter.in.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.ordering.application.command.ProcessOrderPaymentCommand;
import com.template.api.ordering.application.port.in.ProcessOrderPaymentUseCase;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.IdempotencyGate;
import com.template.api.shared.domain.exception.ConflictException;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ValidationException;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventWorker Unit Tests")
class OrderEventWorkerUnitTest {

    @Mock
    private ProcessOrderPaymentUseCase processOrderPaymentUseCase;

    @Mock
    private IdempotencyGate idempotencyGate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor directExecutor = Runnable::run;

    private OrderEventWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OrderEventWorker(
                processOrderPaymentUseCase,
                idempotencyGate,
                objectMapper,
                directExecutor
        );
        ExecutionContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("should_skip_processing_when_event_already_processed")
    void should_skip_processing_when_event_already_processed() {
        UUID eventId = UUID.randomUUID();
        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenReturn(false);

        worker.consumeOrderPaymentEvent(eventId, "{\"orderId\":\"" + UUID.randomUUID() + "\"}");

        verify(processOrderPaymentUseCase, never()).execute(any());
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_deserialize_and_execute_use_case_when_event_is_new")
    void should_deserialize_and_execute_use_case_when_event_is_new() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED"
            }
        """.formatted(orderId);

        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenReturn(true);

        worker.consumeOrderPaymentEvent(eventId, json);

        ArgumentCaptor<ProcessOrderPaymentCommand> captor = ArgumentCaptor.forClass(ProcessOrderPaymentCommand.class);
        verify(processOrderPaymentUseCase).execute(captor.capture());

        ProcessOrderPaymentCommand command = captor.getValue();
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.paymentStatus()).isEqualTo("CONFIRMED");
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_release_idempotency_and_throw_when_downstream_fails_with_uncontrolled_exception")
    void should_release_idempotency_and_throw_when_downstream_fails_with_uncontrolled_exception() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED"
            }
        """.formatted(orderId);

        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenReturn(true);
        doThrow(new RuntimeException("DB Connection Timeout"))
                .when(processOrderPaymentUseCase).execute(any());

        assertThatThrownBy(() -> worker.consumeOrderPaymentEvent(eventId, json).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(InfrastructureException.class);

        verify(idempotencyGate).release("ordering", eventId, "OrderEventWorker");
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_release_idempotency_and_rethrow_when_downstream_fails_with_transient_technical_exception")
    void should_release_idempotency_and_rethrow_when_downstream_fails_with_transient_technical_exception() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED"
            }
        """.formatted(orderId);

        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenReturn(true);
        InfrastructureException technicalEx = new InfrastructureException("Transient connection reset");
        doThrow(technicalEx)
                .when(processOrderPaymentUseCase).execute(any());

        assertThatThrownBy(() -> worker.consumeOrderPaymentEvent(eventId, json).join())
                .isInstanceOf(CompletionException.class)
                .hasCause(technicalEx);

        verify(idempotencyGate).release("ordering", eventId, "OrderEventWorker");
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_not_release_idempotency_and_not_throw_when_downstream_fails_with_conflict_exception")
    void should_not_release_idempotency_and_not_throw_when_downstream_fails_with_conflict_exception() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED"
            }
        """.formatted(orderId);

        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenReturn(true);
        doThrow(new ConflictException("Order already completed"))
                .when(processOrderPaymentUseCase).execute(any());

        CompletableFuture<Void> future = worker.consumeOrderPaymentEvent(eventId, json);
        future.join();

        verify(idempotencyGate, never()).release(any(), any(), any());
        verify(idempotencyGate, never()).release(any(), any());
        verify(idempotencyGate, never()).release(any());
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_not_release_idempotency_and_not_throw_when_downstream_fails_with_validation_exception")
    void should_not_release_idempotency_and_not_throw_when_downstream_fails_with_validation_exception() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED"
            }
        """.formatted(orderId);

        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenReturn(true);
        doThrow(new ValidationException("ORDER_AMOUNT_REQUIRED"))
                .when(processOrderPaymentUseCase).execute(any());

        CompletableFuture<Void> future = worker.consumeOrderPaymentEvent(eventId, json);
        future.join();

        verify(idempotencyGate, never()).release(any(), any(), any());
        verify(idempotencyGate, never()).release(any(), any());
        verify(idempotencyGate, never()).release(any());
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_propagate_tenant_id_before_idempotency_gate_when_present_in_payload")
    void should_propagate_tenant_id_before_idempotency_gate_when_present_in_payload() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED",
                "tenantId": "%s"
            }
        """.formatted(orderId, tenantId);

        AtomicReference<ExecutionContext> contextDuringAcquire = new AtomicReference<>();
        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenAnswer(inv -> {
            contextDuringAcquire.set(ExecutionContextHolder.get());
            return true;
        });

        worker.consumeOrderPaymentEvent(eventId, json);

        assertThat(contextDuringAcquire.get()).isNotNull();
        assertThat(contextDuringAcquire.get().tenantId()).isEqualTo(tenantId);
        assertThat(contextDuringAcquire.get().hasRole("SYSTEM")).isTrue();
        verify(processOrderPaymentUseCase).execute(any());
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("should_propagate_custom_correlation_id_when_present_in_payload")
    void should_propagate_custom_correlation_id_when_present_in_payload() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String customCorrelationId = "corr-async-001";
        String json = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED",
                "tenantId": "%s",
                "correlationId": "%s"
            }
        """.formatted(orderId, tenantId, customCorrelationId);

        AtomicReference<ExecutionContext> contextDuringAcquire = new AtomicReference<>();
        when(idempotencyGate.tryAcquire("ordering", eventId, "OrderEventWorker")).thenAnswer(inv -> {
            contextDuringAcquire.set(ExecutionContextHolder.get());
            return true;
        });

        worker.consumeOrderPaymentEvent(eventId, json);

        assertThat(contextDuringAcquire.get()).isNotNull();
        assertThat(contextDuringAcquire.get().tenantId()).isEqualTo(tenantId);
        assertThat(contextDuringAcquire.get().correlationId()).isEqualTo(customCorrelationId);
        verify(processOrderPaymentUseCase).execute(any());
        assertThat(ExecutionContextHolder.get()).isNull();
    }
}
