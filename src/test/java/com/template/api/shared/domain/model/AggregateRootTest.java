package com.template.api.shared.domain.model;

import com.template.api.shared.domain.event.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AggregateRoot Unit Tests")
class AggregateRootTest {

    record DummyEvent(UUID eventId, String aggregateId, Instant occurredAt, String eventType) implements DomainEvent {}

    static class TestAggregate extends AggregateRoot<UUID> {
        TestAggregate(UUID id) {
            super(id);
        }

        TestAggregate(UUID id, Long version) {
            super(id, version);
        }

        void doAction(String data) {
            registerEvent(new DummyEvent(UUID.randomUUID(), getId() != null ? getId().toString() : "new", Instant.now(), "dummy.event.v1"));
        }
    }

    @Test
    @DisplayName("Should detect if entity is new based on version")
    void should_detect_version_state_when_aggregate_is_inspected() {
        TestAggregate withIdOnly = new TestAggregate(UUID.randomUUID());
        TestAggregate persisted = new TestAggregate(UUID.randomUUID(), 0L);

        assertThat(withIdOnly.id()).isNotNull();
        assertThat(withIdOnly.version()).isNull();
        assertThat(withIdOnly.getVersion()).isNull();

        assertThat(persisted.getVersion()).isEqualTo(0L);
        assertThat(persisted.version()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should lazily handle domain events and clear memory on pull")
    void should_drain_events_atomically_when_pull_domain_events_called() {
        TestAggregate aggregate = new TestAggregate(UUID.randomUUID(), 1L);

        assertThat(aggregate.hasDomainEvents()).isFalse();
        assertThat(aggregate.pullDomainEvents()).isEmpty();

        aggregate.doAction("test1");

        assertThat(aggregate.hasDomainEvents()).isTrue();

        List<DomainEvent> events = aggregate.pullDomainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("dummy.event.v1");
        assertThat(aggregate.hasDomainEvents()).isFalse();
        assertThat(aggregate.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Should throw NullPointerException when registering null event")
    void should_throw_null_pointer_exception_when_registering_null_event() {
        TestAggregate aggregate = new TestAggregate(UUID.randomUUID());

        assertThatThrownBy(() -> aggregate.registerEvent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("AGGREGATE_EVENT_CANNOT_BE_NULL");
    }
}
