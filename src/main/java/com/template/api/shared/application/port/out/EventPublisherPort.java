package com.template.api.shared.application.port.out;

import com.template.api.shared.domain.event.DomainEvent;

import java.util.List;

public interface EventPublisherPort {

    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
}
