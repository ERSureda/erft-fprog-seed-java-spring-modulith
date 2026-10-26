package com.template.api.shared.infrastructure.adapter.out.clock;

import com.template.api.shared.application.port.out.UtcClockPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public final class UtcClockAdapter implements UtcClockPort {

    private final Clock clock;

    public UtcClockAdapter(@Autowired(required = false) Clock clock) {
        this.clock = (clock != null) ? clock : Clock.systemUTC();
    }

    @Override
    public Instant now() {
        return clock.instant();
    }
}
