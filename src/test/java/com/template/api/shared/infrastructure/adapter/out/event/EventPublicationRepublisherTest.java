package com.template.api.shared.infrastructure.adapter.out.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@DisplayName("EventPublicationRepublisher Unit Tests")
class EventPublicationRepublisherTest {

    @Test
    @DisplayName("Should throw NullPointerException when incompletePublications is null")
    void should_throw_null_pointer_exception_when_dependency_is_null() {
        assertThatThrownBy(() -> new EventPublicationRepublisher(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("incompletePublications cannot be null");
    }

    @Test
    @DisplayName("Should trigger resubmission with 1-minute stale threshold")
    void should_delegate_to_incomplete_publications_when_resubmitting_failed_events() {
        IncompleteEventPublications publications = Mockito.mock(IncompleteEventPublications.class);
        EventPublicationRepublisher republisher = new EventPublicationRepublisher(publications);

        republisher.resubmitFailedEvents();

        verify(publications).resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
    }
}
