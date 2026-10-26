package com.template.api.ordering.domain.event;

import com.template.api.shared.domain.event.DomainEvent;

import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        UUID customerId
) implements DomainEvent {

    @Override
    public String aggregateId() {
        return orderId.toString();
    }
}
