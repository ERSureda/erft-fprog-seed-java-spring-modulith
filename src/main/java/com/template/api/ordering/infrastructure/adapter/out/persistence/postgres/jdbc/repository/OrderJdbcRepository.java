package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.repository;

import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.mapper.OrderResultRowMapper;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.query.OrderJdbcQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderJdbcRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OrderResultRowMapper rowMapper;

    public Optional<OrderResult> findOrderResultById(UUID orderId) {
        return jdbcTemplate.query(OrderJdbcQueries.SELECT_ORDER_BY_ID, Map.of("id", orderId), rowMapper)
                .stream().findFirst();
    }
}
