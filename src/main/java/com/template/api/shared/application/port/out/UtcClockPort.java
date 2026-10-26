package com.template.api.shared.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public interface UtcClockPort {

    Instant now();

    default LocalDate todayUtc() {
        return LocalDate.ofInstant(now(), ZoneOffset.UTC);
    }
}
