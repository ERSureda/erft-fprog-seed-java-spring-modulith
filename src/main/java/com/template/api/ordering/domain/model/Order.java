package com.template.api.ordering.domain.model;

import com.template.api.ordering.domain.OrderingError;
import com.template.api.ordering.domain.event.OrderCancelledEvent;
import com.template.api.ordering.domain.event.OrderConfirmedEvent;
import com.template.api.ordering.domain.event.OrderCreatedEvent;
import com.template.api.ordering.domain.event.OrderShippedEvent;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.shared.domain.exception.ConflictException;
import com.template.api.shared.domain.model.AggregateRoot;
import com.template.api.shared.domain.valueobject.Money;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Order extends AggregateRoot<UUID> {

    private final UUID tenantId;
    private final UUID customerId;
    private Money amount;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    // --- Constructors & Factories ---
    private Order(
            UUID id,
            UUID tenantId,
            UUID customerId,
            Money amount,
            OrderStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        super(Objects.requireNonNull(id, "ORDER_ID_CANNOT_BE_NULL"), version);
        this.tenantId = Objects.requireNonNull(tenantId, "ORDER_TENANT_ID_CANNOT_BE_NULL");
        this.customerId = Objects.requireNonNull(customerId, "ORDER_CUSTOMER_ID_CANNOT_BE_NULL");
        this.amount = Objects.requireNonNull(amount, "ORDER_AMOUNT_CANNOT_BE_NULL");
        this.status = Objects.requireNonNull(status, "ORDER_STATUS_CANNOT_BE_NULL");
        this.createdAt = Objects.requireNonNull(createdAt, "ORDER_CREATED_AT_CANNOT_BE_NULL");
        this.updatedAt = Objects.requireNonNull(updatedAt, "ORDER_UPDATED_AT_CANNOT_BE_NULL");
    }

    public static Order create(UUID id, UUID tenantId, UUID customerId, Money amount) {
        return create(id, tenantId, customerId, amount, Instant.now());
    }

    public static Order create(UUID id, UUID tenantId, UUID customerId, Money amount, Instant now) {
        Instant timestamp = Objects.requireNonNull(now, "ORDER_CREATED_AT_CANNOT_BE_NULL");
        Order order = new Order(id, tenantId, customerId, amount, OrderStatus.PENDING, 0L, timestamp, timestamp);
        order.registerEvent(new OrderCreatedEvent(
                id,
                customerId,
                amount.amount(),
                amount.currency().getCurrencyCode()
        ));
        return order;
    }

    public static Order reconstruct(
            UUID id,
            UUID tenantId,
            UUID customerId,
            Money amount,
            OrderStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Order(id, tenantId, customerId, amount, status, version, createdAt, updatedAt);
    }

    public boolean canConfirm() {
        return this.status == OrderStatus.PENDING;
    }

    public void confirm() {
        confirm(Instant.now());
    }

    public void confirm(Instant now) {
        if (!canConfirm()) {
            throw new ConflictException(OrderingError.ORDERING_ORDER_INVALID_STATUS);
        }
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Objects.requireNonNull(now, "ORDER_UPDATED_AT_CANNOT_BE_NULL");
        this.registerEvent(new OrderConfirmedEvent(this.id, this.customerId));
    }

    public boolean canShip() {
        return this.status == OrderStatus.CONFIRMED;
    }

    public void ship() {
        ship(Instant.now());
    }

    public void ship(Instant now) {
        if (!canShip()) {
            throw new ConflictException(OrderingError.ORDERING_ORDER_NOT_CONFIRMED);
        }
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = Objects.requireNonNull(now, "ORDER_UPDATED_AT_CANNOT_BE_NULL");
        this.registerEvent(new OrderShippedEvent(this.id, this.customerId));
    }

    public boolean canCancel() {
        return this.status != OrderStatus.SHIPPED && this.status != OrderStatus.CANCELLED;
    }

    public void cancel() {
        cancel(Instant.now());
    }

    public void cancel(Instant now) {
        if (!canCancel()) {
            throw new ConflictException(OrderingError.ORDERING_ORDER_ALREADY_SHIPPED);
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Objects.requireNonNull(now, "ORDER_UPDATED_AT_CANNOT_BE_NULL");
        this.registerEvent(new OrderCancelledEvent(this.id, this.customerId));
    }

    // --- Getters ---
    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Money getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
