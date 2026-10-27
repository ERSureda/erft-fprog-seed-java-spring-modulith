package com.template.api.shared.application.port.out;

import com.template.api.shared.domain.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public interface OutboxPublisherPort {

    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
    void publish(String moduleSchema, String aggregateType, UUID aggregateId, DomainEvent event);

    default void publishAll(String moduleSchema, String aggregateType, UUID aggregateId, List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            publish(moduleSchema, aggregateType, aggregateId, event);
        }
    }
}
