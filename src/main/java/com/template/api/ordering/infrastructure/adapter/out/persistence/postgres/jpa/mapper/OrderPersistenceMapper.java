package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.mapper;

import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.entity.OrderEntity;
import com.template.api.shared.domain.valueobject.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Currency;

@Component
public class OrderPersistenceMapper {

    public OrderEntity toEntity(Order domain) {
        if (domain == null) {
            return null;
        }

        BigDecimal amount = domain.getAmount() != null ? domain.getAmount().amount() : null;
        String currency = (domain.getAmount() != null && domain.getAmount().currency() != null)
                ? domain.getAmount().currency().getCurrencyCode()
                : null;
        String status = domain.getStatus() != null ? domain.getStatus().name() : null;

        return new OrderEntity(
                domain.getId(),
                domain.getTenantId(),
                domain.getCustomerId(),
                amount,
                currency,
                status,
                domain.getVersion(),
                domain.getCreatedAt(),
                domain.getUpdatedAt(),
                null
        );
    }

    public Order toDomain(OrderEntity entity) {
        if (entity == null || entity.getDeletedAt() != null) {
            return null;
        }

        Money money = Money.of(entity.getAmount(), Currency.getInstance(entity.getCurrency()));
        OrderStatus status = OrderStatus.valueOf(entity.getStatus());

        return Order.reconstruct(
                entity.getId(),
                entity.getTenantId(),
                entity.getCustomerId(),
                money,
                status,
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
