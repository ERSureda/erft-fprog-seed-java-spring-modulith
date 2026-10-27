package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.adapter;

import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.mapper.OrderResultRowMapper;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.repository.OrderJdbcRepository;
import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderJdbcQueryAdapter.class, OrderJdbcRepository.class, OrderResultRowMapper.class})
@DisplayName("OrderJdbcQueryAdapter CQRS Projection Tests")
class OrderJdbcQueryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderJdbcQueryAdapter queryAdapter;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Should project order result directly from database when order exists")
    void should_project_order_result_directly_from_database_when_order_exists() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        jdbcTemplate.getJdbcTemplate().execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', false)");
        jdbcTemplate.update("""
            INSERT INTO ordering.orders (id, tenant_id, customer_id, amount, currency, status, version)
            VALUES (:id, :tenantId, :customerId, :amount, :currency, :status, 0)
        """, Map.of(
                "id", orderId,
                "tenantId", tenantId,
                "customerId", customerId,
                "amount", new BigDecimal("75.50"),
                "currency", "EUR",
                "status", "PENDING"
        ));

        Optional<OrderResult> result = queryAdapter.findOrderResultById(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(orderId);
        assertThat(result.get().status()).isEqualTo("PENDING");
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("75.50"));
        assertThat(result.get().currency()).isEqualTo("EUR");
    }
}
