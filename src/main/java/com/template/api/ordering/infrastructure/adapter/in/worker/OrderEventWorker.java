package com.template.api.ordering.infrastructure.adapter.in.worker;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.ordering.application.port.in.ProcessOrderPaymentUseCase;
import com.template.api.ordering.infrastructure.adapter.in.worker.dto.OrderPaymentEventMessage;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.IdempotencyGate;
import com.template.api.shared.domain.exception.BaseException;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderEventWorker {

    private static final String CONSUMER_NAME = "OrderEventWorker";
    private static final String MODULE_SCHEMA = "ordering";

    private final ProcessOrderPaymentUseCase processOrderPaymentUseCase;
    private final IdempotencyGate idempotencyGate;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public CompletableFuture<Void> consumeOrderPaymentEvent(UUID eventId, String payloadJson) {
        return CompletableFuture.runAsync(() -> {
            final OrderPaymentEventMessage message;
            try {
                message = objectMapper.readValue(payloadJson, OrderPaymentEventMessage.class);
            } catch (JacksonException ex) {
                log.warn("Invalid JSON payload for order payment event [{}], skipping retry: {}", eventId, ex.getMessage());
                return;
            }

            String correlationId = (message.correlationId() != null && !message.correlationId().isBlank())
                    ? message.correlationId()
                    : eventId.toString();
            ExecutionContext context = new ExecutionContext(message.tenantId(), null, Set.of("SYSTEM"), correlationId);
            ExecutionContextHolder.runWith(context, () -> {
                if (!idempotencyGate.tryAcquire(MODULE_SCHEMA, eventId, CONSUMER_NAME)) {
                    log.info("Event [{}] already processed by [{}]. Skipping duplicate.", eventId, CONSUMER_NAME);
                    return;
                }
                try {
                    processOrderPaymentUseCase.execute(message.toCommand());
                } catch (BaseException ex) {
                    if (ex.category().capturesDiagnostics()) {
                        idempotencyGate.release(MODULE_SCHEMA, eventId, CONSUMER_NAME);
                        log.error("Technical failure processing order payment event [{}]", eventId, ex);
                        throw ex;
                    }
                    log.warn("Business validation error processing order payment event [{}], skipping retry: {}", eventId, ex.getMessage());
                } catch (Exception ex) {
                    idempotencyGate.release(MODULE_SCHEMA, eventId, CONSUMER_NAME);
                    log.error("Failed to process order payment event [{}]", eventId, ex);
                    throw new InfrastructureException("Error processing event " + eventId, ex);
                }
            });
        }, executor);
    }
}
