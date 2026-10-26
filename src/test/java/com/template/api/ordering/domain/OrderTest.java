package com.template.api.ordering.domain;

import com.template.api.ordering.domain.event.OrderCancelledEvent;
import com.template.api.ordering.domain.event.OrderConfirmedEvent;
import com.template.api.ordering.domain.event.OrderCreatedEvent;
import com.template.api.ordering.domain.event.OrderShippedEvent;
import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.shared.domain.exception.ConflictException;
import com.template.api.shared.domain.exception.ValidationException;
import com.template.api.shared.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order Aggregate & Value Object Domain Tests")
class OrderTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final Money amount = Money.of(new BigDecimal("150.00"), Currency.getInstance("EUR"));

    @Test
    @DisplayName("should_register_order_created_event_when_order_is_created")
    void should_register_order_created_event_when_order_is_created() {
        UUID orderId = UUID.randomUUID();

        Order order = Order.create(orderId, tenantId, customerId, amount);

        assertThat(order.getId()).isEqualTo(orderId);
        assertThat(order.getTenantId()).isEqualTo(tenantId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getAmount()).isEqualTo(amount);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.canConfirm()).isTrue();
        assertThat(order.canShip()).isFalse();
        assertThat(order.canCancel()).isTrue();
        assertThat(order.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    @DisplayName("should_throw_validation_exception_when_amount_is_negative")
    void should_throw_validation_exception_when_amount_is_negative() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-10.00"), Currency.getInstance("EUR")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("should_throw_null_pointer_exception_when_order_id_is_null_in_creation")
    void should_throw_null_pointer_exception_when_order_id_is_null_in_creation() {
        Money validAmount = Money.of(new BigDecimal("50.00"), Currency.getInstance("EUR"));

        assertThatThrownBy(() -> Order.create(null, tenantId, customerId, validAmount))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ORDER_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("should_throw_null_pointer_exception_when_tenant_id_is_null_in_creation")
    void should_throw_null_pointer_exception_when_tenant_id_is_null_in_creation() {
        UUID orderId = UUID.randomUUID();
        Money validAmount = Money.of(new BigDecimal("50.00"), Currency.getInstance("EUR"));

        assertThatThrownBy(() -> Order.create(orderId, null, customerId, validAmount))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ORDER_TENANT_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("should_throw_null_pointer_exception_when_customer_id_is_null_in_creation")
    void should_throw_null_pointer_exception_when_customer_id_is_null_in_creation() {
        UUID orderId = UUID.randomUUID();
        Money validAmount = Money.of(new BigDecimal("50.00"), Currency.getInstance("EUR"));

        assertThatThrownBy(() -> Order.create(orderId, tenantId, null, validAmount))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ORDER_CUSTOMER_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("should_throw_null_pointer_exception_when_amount_is_null_in_creation")
    void should_throw_null_pointer_exception_when_amount_is_null_in_creation() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> Order.create(orderId, tenantId, customerId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ORDER_AMOUNT_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("should_transition_status_and_register_events_when_valid_business_actions_are_executed")
    void should_transition_status_and_register_events_when_valid_business_actions_are_executed() {
        Order order = Order.create(UUID.randomUUID(), tenantId, customerId, Money.of(new BigDecimal("99.99"), Currency.getInstance("EUR")));
        order.pullDomainEvents();

        order.confirm();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.canConfirm()).isFalse();
        assertThat(order.canShip()).isTrue();
        assertThat(order.canCancel()).isTrue();
        assertThat(order.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(OrderConfirmedEvent.class);

        order.ship();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(order.canConfirm()).isFalse();
        assertThat(order.canShip()).isFalse();
        assertThat(order.canCancel()).isFalse();
        assertThat(order.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(OrderShippedEvent.class);

        assertThatThrownBy(order::cancel)
                .isInstanceOf(ConflictException.class)
                .hasMessage(OrderingError.ORDERING_ORDER_ALREADY_SHIPPED.code());
    }

    @Test
    @DisplayName("should_cancel_order_and_register_event_when_order_is_pending")
    void should_cancel_order_and_register_event_when_order_is_pending() {
        Order order = Order.create(UUID.randomUUID(), tenantId, customerId, Money.of(new BigDecimal("99.99"), Currency.getInstance("EUR")));
        order.pullDomainEvents();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.canConfirm()).isFalse();
        assertThat(order.canShip()).isFalse();
        assertThat(order.canCancel()).isFalse();
        assertThat(order.pullDomainEvents())
                .hasSize(1)
                .first()
                .isInstanceOf(OrderCancelledEvent.class);
    }

    @Test
    @DisplayName("should_throw_conflict_exception_when_confirming_cancelled_order")
    void should_throw_conflict_exception_when_confirming_cancelled_order() {
        Order order = Order.create(UUID.randomUUID(), tenantId, customerId, Money.of(new BigDecimal("99.99"), Currency.getInstance("EUR")));
        order.cancel();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(ConflictException.class)
                .hasMessage(OrderingError.ORDERING_ORDER_INVALID_STATUS.code());
    }

    @Test
    @DisplayName("should_throw_conflict_exception_when_shipping_unconfirmed_order")
    void should_throw_conflict_exception_when_shipping_unconfirmed_order() {
        Order order = Order.create(UUID.randomUUID(), tenantId, customerId, Money.of(new BigDecimal("99.99"), Currency.getInstance("EUR")));

        assertThatThrownBy(order::ship)
                .isInstanceOf(ConflictException.class)
                .hasMessage(OrderingError.ORDERING_ORDER_NOT_CONFIRMED.code());
    }
}
