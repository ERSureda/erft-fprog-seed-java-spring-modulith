package com.template.api.ordering.application.service;

import com.template.api.ordering.application.command.CreateOrderCommand;
import com.template.api.ordering.application.port.in.CreateOrderUseCase;
import com.template.api.ordering.application.port.out.OrderRepositoryPort;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.domain.model.Order;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.ExecutionContextPort;
import com.template.api.shared.application.port.out.UtcClockPort;
import com.template.api.shared.application.port.out.UuidGeneratorPort;
import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.FieldViolation;
import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.ValidationException;
import com.template.api.shared.domain.valueobject.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ExecutionContextPort executionContextPort;
    private final UuidGeneratorPort uuidGeneratorPort;
    private final UtcClockPort utcClockPort;

    @Override
    @Transactional
    public OrderResult execute(CreateOrderCommand command) {
        ExecutionContext context = executionContextPort.current();
        if (context == null || !context.hasTenant()) {
            throw new ForbiddenException("Missing required tenant context for order creation");
        }
        UUID tenantId = context.requireTenantId();

        UUID customerId = command.customerId();
        if (customerId == null && context.userId() != null) {
            customerId = context.userId();
        }
        if (customerId == null) {
            customerId = uuidGeneratorPort.generateId();
        }

        Currency currency;
        try {
            currency = Currency.getInstance(command.currency() != null ? command.currency() : "EUR");
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(CommonError.VALIDATION_FAILED, "ORDER_CURRENCY_INVALID_CODE",
                    List.of(new FieldViolation("currency", "ORDER_CURRENCY_INVALID_CODE")));
        }
        Money money = Money.of(command.amount(), currency);

        UUID orderId = uuidGeneratorPort.generateId();
        Instant now = utcClockPort.now();
        Order order = Order.create(orderId, tenantId, customerId, money, now);
        Order savedOrder = orderRepository.save(order);

        return new OrderResult(
                savedOrder.getId(),
                savedOrder.getStatus().name(),
                savedOrder.getAmount().amount(),
                savedOrder.getAmount().currency().getCurrencyCode()
        );
    }
}
