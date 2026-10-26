package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.mapper;

import com.template.api.ordering.application.result.OrderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderResultRowMapper Unit Tests")
class OrderResultRowMapperTest {

    @Mock
    private ResultSet resultSet;

    private final OrderResultRowMapper mapper = new OrderResultRowMapper();

    @Test
    @DisplayName("should_map_result_set_row_to_order_result_when_row_is_valid")
    void should_map_result_set_row_to_order_result_when_row_is_valid() throws SQLException {
        UUID orderId = UUID.randomUUID();
        when(resultSet.getObject("id", UUID.class)).thenReturn(orderId);
        when(resultSet.getString("status")).thenReturn("CONFIRMED");
        when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("120.50"));
        when(resultSet.getString("currency")).thenReturn("EUR");

        OrderResult result = mapper.mapRow(resultSet, 1);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("120.50"));
        assertThat(result.currency()).isEqualTo("EUR");
    }
}
