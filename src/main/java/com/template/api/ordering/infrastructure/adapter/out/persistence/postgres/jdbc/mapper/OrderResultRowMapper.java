package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.mapper;

import com.template.api.ordering.application.result.OrderResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class OrderResultRowMapper implements RowMapper<OrderResult> {

    @Override
    public OrderResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OrderResult(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getBigDecimal("amount"),
                rs.getString("currency")
        );
    }
}
