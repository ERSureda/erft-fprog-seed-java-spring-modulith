package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.query;

public final class OrderJdbcQueries {

    private OrderJdbcQueries() {}

    public static final String SELECT_ORDER_BY_ID = """
        SELECT id, status, amount, currency
        FROM ordering.orders
        WHERE id = :id
          AND deleted_at IS NULL
        """;
}
