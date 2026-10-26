package com.template.api.ordering.application.query;

import java.util.UUID;

public record GetOrderByIdQuery(
        UUID orderId
) {
}
