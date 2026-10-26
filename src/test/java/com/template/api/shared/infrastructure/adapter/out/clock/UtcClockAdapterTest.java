package com.template.api.shared.infrastructure.adapter.out.clock;

import com.template.api.shared.application.port.out.UtcClockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UtcClockAdapter Unit Tests")
class UtcClockAdapterTest {

    @Test
    @DisplayName("Should implement UtcClockPort and default to system UTC when clock is null")
    void should_default_to_system_utc_when_clock_is_null() {
        UtcClockAdapter clockAdapter = new UtcClockAdapter(null);

        assertThat(clockAdapter).isInstanceOf(UtcClockPort.class);

        Instant before = Instant.now();
        Instant clockInstant = clockAdapter.now();
        Instant after = Instant.now();

        assertThat(clockInstant).isNotNull();
        assertThat(clockInstant).isBetween(before.minus(Duration.ofMillis(100)), after.plus(Duration.ofMillis(100)));
    }

    @Test
    @DisplayName("Should return fixed instant when configured with fixed clock")
    void should_return_fixed_instant_when_configured_with_fixed_clock() {
        Instant fixedInstant = Instant.parse("2026-09-12T10:15:30.00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        UtcClockAdapter clockAdapter = new UtcClockAdapter(fixedClock);

        Instant result = clockAdapter.now();

        assertThat(result).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("Should correctly calculate todayUtc via default method")
    void should_return_current_date_in_utc_when_today_utc_invoked() {
        Instant fixedInstant = Instant.parse("2026-09-12T23:59:59.00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        UtcClockAdapter clockAdapter = new UtcClockAdapter(fixedClock);

        LocalDate today = clockAdapter.todayUtc();

        assertThat(today).isEqualTo(LocalDate.of(2026, 9, 12));
    }
}
