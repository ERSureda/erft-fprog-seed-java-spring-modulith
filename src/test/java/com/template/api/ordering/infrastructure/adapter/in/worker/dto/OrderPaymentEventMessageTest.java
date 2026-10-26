package com.template.api.ordering.infrastructure.adapter.in.worker.dto;

import com.template.api.ordering.application.command.ProcessOrderPaymentCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderPaymentEventMessage Unit Tests")
class OrderPaymentEventMessageTest {

    @Test
    @DisplayName("toCommand should map fields into ProcessOrderPaymentCommand with provided status")
    void should_map_fields_when_status_is_explicit() {
        UUID orderId = UUID.randomUUID();
        OrderPaymentEventMessage message = new OrderPaymentEventMessage(orderId, "FAILED");

        ProcessOrderPaymentCommand command = message.toCommand();

        assertThat(command).isNotNull();
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.paymentStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("toCommand should default paymentStatus to CONFIRMED when null")
    void should_default_status_to_confirmed_when_status_is_null() {
        UUID orderId = UUID.randomUUID();
        OrderPaymentEventMessage message = new OrderPaymentEventMessage(orderId, null);

        ProcessOrderPaymentCommand command = message.toCommand();

        assertThat(command).isNotNull();
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.paymentStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("canonical constructor should preserve tenantId and correlationId metadata")
    void should_preserve_tenant_and_correlation_metadata_when_canonical_constructor_used() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrderPaymentEventMessage message = new OrderPaymentEventMessage(
                orderId, "CONFIRMED", tenantId, "corr-123"
        );

        assertThat(message.orderId()).isEqualTo(orderId);
        assertThat(message.paymentStatus()).isEqualTo("CONFIRMED");
        assertThat(message.tenantId()).isEqualTo(tenantId);
        assertThat(message.correlationId()).isEqualTo("corr-123");
    }
}
