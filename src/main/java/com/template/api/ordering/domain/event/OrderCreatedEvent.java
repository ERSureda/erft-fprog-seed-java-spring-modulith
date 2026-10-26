package com.template.api.ordering.domain.event;

import com.template.api.shared.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return orderId.toString();
    }
}
