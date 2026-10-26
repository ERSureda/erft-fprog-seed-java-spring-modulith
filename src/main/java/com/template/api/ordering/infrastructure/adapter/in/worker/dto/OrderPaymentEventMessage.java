package com.template.api.ordering.infrastructure.adapter.in.worker.dto;

import com.template.api.ordering.application.command.ProcessOrderPaymentCommand;

import java.util.UUID;

public record OrderPaymentEventMessage(
        UUID orderId,
        String paymentStatus,
        UUID tenantId,
        String correlationId
) {

    public OrderPaymentEventMessage(UUID orderId, String paymentStatus, UUID tenantId) {
        this(orderId, paymentStatus, tenantId, null);
    }

    public OrderPaymentEventMessage(UUID orderId, String paymentStatus) {
        this(orderId, paymentStatus, null, null);
    }

    public ProcessOrderPaymentCommand toCommand() {
        return new ProcessOrderPaymentCommand(
                this.orderId,
                this.paymentStatus != null ? this.paymentStatus : "CONFIRMED"
        );
    }
}
