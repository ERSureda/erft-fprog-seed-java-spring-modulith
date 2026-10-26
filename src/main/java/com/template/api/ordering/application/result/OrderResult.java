package com.template.api.ordering.application.result;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResult(
        UUID id,
        String status,
        BigDecimal amount,
        String currency
) {
}
