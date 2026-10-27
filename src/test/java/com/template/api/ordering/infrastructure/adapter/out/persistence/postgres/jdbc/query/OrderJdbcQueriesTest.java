package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderJdbcQueries Unit Tests")
class OrderJdbcQueriesTest {

    @Test
    @DisplayName("should_define_valid_query_when_selecting_order_by_id")
    void should_define_valid_query_when_selecting_order_by_id() {
        String query = OrderJdbcQueries.SELECT_ORDER_BY_ID;

        assertThat(query)
                .isNotBlank()
                .contains("SELECT id, status, amount, currency")
                .contains("FROM ordering.orders")
                .contains("WHERE id = :id")
                .contains("AND deleted_at IS NULL");
    }
}
