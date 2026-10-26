package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.adapter;

import com.template.api.ordering.application.port.out.OrderQueryPort;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.repository.OrderJdbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderJdbcQueryAdapter implements OrderQueryPort {

    private final OrderJdbcRepository repository;

    @Override
    public Optional<OrderResult> findOrderResultById(UUID orderId) {
        return repository.findOrderResultById(orderId);
    }
}
