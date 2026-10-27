package com.template.api.shared.infrastructure.adapter.out.event;

import com.template.api.shared.domain.event.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SpringEventPublisherAdapter Unit Tests")
class SpringEventPublisherAdapterTest {

    record DummyEvent(UUID eventId, String aggregateId, Instant occurredAt, String eventType) implements DomainEvent {}

    @Test
    @DisplayName("Should throw NullPointerException when publisher is null")
    void should_throw_null_pointer_exception_when_publisher_is_null() {
        assertThatThrownBy(() -> new SpringEventPublisherAdapter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("EVENT_PUBLISHER_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("Should publish domain event to ApplicationEventPublisher")
    void should_delegate_to_publisher_when_valid_event_published() {
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        SpringEventPublisherAdapter adapter = new SpringEventPublisherAdapter(publisher);

        DomainEvent event = new DummyEvent(UUID.randomUUID(), "agg-1", Instant.now(), "dummy.v1");

        adapter.publish(event);

        verify(publisher).publishEvent(event);
    }

    @Test
    @DisplayName("Should silently ignore null event in publish")
    void should_not_publish_when_event_is_null() {
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        SpringEventPublisherAdapter adapter = new SpringEventPublisherAdapter(publisher);

        adapter.publish(null);

        verify(publisher, never()).publishEvent(Mockito.any());
    }

    @Test
    @DisplayName("Should publish all non-null domain events in publishAll")
    void should_publish_all_events_when_valid_list_provided() {
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        SpringEventPublisherAdapter adapter = new SpringEventPublisherAdapter(publisher);

        DomainEvent event1 = new DummyEvent(UUID.randomUUID(), "agg-1", Instant.now(), "event.1");
        DomainEvent event2 = new DummyEvent(UUID.randomUUID(), "agg-2", Instant.now(), "event.2");

        adapter.publishAll(List.of(event1, event2));

        verify(publisher).publishEvent(event1);
        verify(publisher).publishEvent(event2);
    }

    @Test
    @DisplayName("Should ignore null or empty lists, and skip null elements in publishAll")
    void should_handle_gracefully_when_list_contains_nulls_or_is_empty() {
        ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);
        SpringEventPublisherAdapter adapter = new SpringEventPublisherAdapter(publisher);

        adapter.publishAll(null);
        adapter.publishAll(List.of());

        DomainEvent validEvent = new DummyEvent(UUID.randomUUID(), "agg-1", Instant.now(), "event.valid");
        adapter.publishAll(Arrays.asList(null, validEvent, null));

        verify(publisher, Mockito.times(1)).publishEvent(validEvent);
    }
}
