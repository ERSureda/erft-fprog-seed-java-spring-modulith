package com.template.api.ordering.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderCommand(
        UUID customerId,
        BigDecimal amount,
        String currency
) {
}
