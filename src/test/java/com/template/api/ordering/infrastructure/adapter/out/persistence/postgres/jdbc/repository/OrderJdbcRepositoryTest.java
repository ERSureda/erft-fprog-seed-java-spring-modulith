package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.repository;

import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jdbc.mapper.OrderResultRowMapper;
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
@Import({OrderJdbcRepository.class, OrderResultRowMapper.class})
@DisplayName("OrderJdbcRepository Integration Tests")
class OrderJdbcRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderJdbcRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should_find_order_result_by_id_when_order_exists")
    void should_find_order_result_by_id_when_order_exists() {
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
                "amount", new BigDecimal("120.00"),
                "currency", "EUR",
                "status", "PENDING"
        ));

        Optional<OrderResult> result = repository.findOrderResultById(orderId);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(orderId);
        assertThat(result.get().status()).isEqualTo("PENDING");
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(result.get().currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("should_return_empty_when_order_does_not_exist")
    void should_return_empty_when_order_does_not_exist() {
        UUID missingId = UUID.randomUUID();

        Optional<OrderResult> result = repository.findOrderResultById(missingId);

        assertThat(result).isEmpty();
    }
}
