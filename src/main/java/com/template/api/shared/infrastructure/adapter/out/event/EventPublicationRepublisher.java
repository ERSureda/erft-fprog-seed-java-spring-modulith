package com.template.api.shared.infrastructure.adapter.out.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@Component
@ConditionalOnClass(IncompleteEventPublications.class)
@ConditionalOnBean(IncompleteEventPublications.class)
@ConditionalOnProperty(prefix = "application.events", name = "resubmit-enabled", havingValue = "true", matchIfMissing = true)
public class EventPublicationRepublisher {

    private static final Duration DEFAULT_STALE_THRESHOLD = Duration.ofMinutes(1);

    private final IncompleteEventPublications incompletePublications;

    public EventPublicationRepublisher(IncompleteEventPublications incompletePublications) {
        this.incompletePublications = Objects.requireNonNull(incompletePublications, "EVENT_REPUBLISHER_PUBLICATIONS_CANNOT_BE_NULL");
    }

    @Scheduled(fixedDelayString = "${application.events.resubmit-interval:10000}")
    public void resubmitFailedEvents() {
        log.debug("Scanning for stalled event publications older than {}...", DEFAULT_STALE_THRESHOLD);
        incompletePublications.resubmitIncompletePublicationsOlderThan(DEFAULT_STALE_THRESHOLD);
    }
}
