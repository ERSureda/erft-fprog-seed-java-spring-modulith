package com.template.api.ordering.fixture;

import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.shared.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public final class OrderFixture {

    public static final Currency DEFAULT_CURRENCY = Currency.getInstance("EUR");
    public static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("100.00");

    private OrderFixture() {}

    public static Money createMoney() {
        return Money.of(DEFAULT_AMOUNT, DEFAULT_CURRENCY);
    }

    public static Money createMoney(BigDecimal amount) {
        return Money.of(amount, DEFAULT_CURRENCY);
    }

    public static Money createMoney(BigDecimal amount, String currencyCode) {
        return Money.of(amount, Currency.getInstance(currencyCode));
    }

    public static Money createMoney(BigDecimal amount, Currency currency) {
        return Money.of(amount, currency);
    }

    public static Order createPendingOrder(UUID tenantId, UUID customerId) {
        return Order.create(
                UUID.randomUUID(),
                tenantId,
                customerId,
                createMoney()
        );
    }

    public static Order createPendingOrder(UUID id, UUID tenantId, UUID customerId, Money amount) {
        return Order.create(id, tenantId, customerId, amount);
    }

    public static Order createOrder(UUID id, UUID tenantId, UUID customerId, Money amount) {
        return Order.create(id, tenantId, customerId, amount);
    }

    public static Order createConfirmedOrder(UUID tenantId, UUID customerId) {
        Order order = createPendingOrder(tenantId, customerId);
        order.pullDomainEvents();
        order.confirm();
        return order;
    }

    public static Order reconstructOrder(
            UUID id,
            UUID tenantId,
            UUID customerId,
            Money amount,
            OrderStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return Order.reconstruct(id, tenantId, customerId, amount, status, version, createdAt, updatedAt);
    }
}
