package com.template.api.ordering.application.port.out;

import com.template.api.ordering.application.result.OrderResult;

import java.util.Optional;
import java.util.UUID;

public interface OrderQueryPort {

    Optional<OrderResult> findOrderResultById(UUID orderId);
}
