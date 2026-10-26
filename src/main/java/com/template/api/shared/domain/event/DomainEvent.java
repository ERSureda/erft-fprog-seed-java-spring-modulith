package com.template.api.shared.domain.event;

import java.time.Instant;

public interface DomainEvent {

    String aggregateId();

    default Instant occurredAt() {
        return Instant.now();
    }

    default String eventType() {
        return getClass().getName();
    }
}
