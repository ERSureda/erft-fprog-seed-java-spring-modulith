package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.mapper;

import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.entity.OrderEntity;
import com.template.api.shared.domain.valueobject.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderPersistenceMapper Unit Tests")
class OrderPersistenceMapperTest {

    private OrderPersistenceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrderPersistenceMapper();
    }

    @Test
    @DisplayName("Should map domain order to entity when domain order provided")
    void should_map_domain_order_to_entity_when_domain_order_provided() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = Order.create(orderId, tenantId, customerId, Money.of(new BigDecimal("150.00"), Currency.getInstance("USD")));

        OrderEntity entity = mapper.toEntity(order);

        assertThat(entity.getId()).isEqualTo(order.getId());
        assertThat(entity.getTenantId()).isEqualTo(tenantId);
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(entity.getCurrency()).isEqualTo("USD");
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(entity.getVersion()).isEqualTo(0L);
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should map entity to domain order when entity provided")
    void should_map_entity_to_domain_order_when_entity_provided() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        OrderEntity entity = new OrderEntity(
                orderId,
                tenantId,
                customerId,
                new BigDecimal("99.99"),
                "EUR",
                "CONFIRMED",
                1L,
                now,
                now,
                null
        );

        Order order = mapper.toDomain(entity);

        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getTenantId()).isEqualTo(tenantId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getAmount().amount()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(order.getAmount().currency().getCurrencyCode()).isEqualTo("EUR");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getVersion()).isEqualTo(1L);
        assertThat(order.getCreatedAt()).isEqualTo(now);
        assertThat(order.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should return null when source is null")
    void should_return_null_when_source_is_null() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("Should return null when entity is soft deleted")
    void should_return_null_when_entity_is_soft_deleted() {
        OrderEntity entity = new OrderEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("99.99"),
                "EUR",
                "CONFIRMED",
                1L,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        assertThat(mapper.toDomain(entity)).isNull();
    }
}
