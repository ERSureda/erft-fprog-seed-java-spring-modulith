package com.template.api.ordering.application.command;

import java.util.Objects;
import java.util.UUID;

public record ProcessOrderPaymentCommand(
        UUID orderId,
        String paymentStatus
) {

    public ProcessOrderPaymentCommand {
        Objects.requireNonNull(orderId, "ORDER_ID_CANNOT_BE_NULL");
        Objects.requireNonNull(paymentStatus, "ORDER_PAYMENT_STATUS_CANNOT_BE_NULL");
    }
}
