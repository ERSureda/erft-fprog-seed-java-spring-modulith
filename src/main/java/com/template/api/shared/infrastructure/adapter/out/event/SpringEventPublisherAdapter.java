package com.template.api.shared.infrastructure.adapter.out.event;

import com.template.api.shared.application.port.out.EventPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public final class SpringEventPublisherAdapter implements EventPublisherPort {

    private final ApplicationEventPublisher publisher;

    public SpringEventPublisherAdapter(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
    }

    @Override
    public void publish(DomainEvent event) {
        if (event != null) {
            publisher.publishEvent(event);
        }
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            if (event != null) {
                publisher.publishEvent(event);
            }
        }
    }
}
