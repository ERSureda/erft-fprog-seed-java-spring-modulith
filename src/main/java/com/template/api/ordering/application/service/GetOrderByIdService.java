package com.template.api.ordering.application.service;

import com.template.api.ordering.application.port.in.GetOrderByIdUseCase;
import com.template.api.ordering.application.port.out.OrderQueryPort;
import com.template.api.ordering.application.query.GetOrderByIdQuery;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.domain.OrderingError;
import com.template.api.ordering.domain.model.Order;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.ExecutionContextPort;
import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetOrderByIdService implements GetOrderByIdUseCase {

    private final OrderQueryPort orderQueryPort;
    private final ExecutionContextPort executionContextPort;

    @Override
    @Transactional(readOnly = true)
    public OrderResult execute(GetOrderByIdQuery query) {
        ExecutionContext context = executionContextPort.current();
        if (context == null || !context.hasTenant()) {
            throw new ForbiddenException("Missing required tenant context for order query");
        }
        context.requireTenantId();
        return orderQueryPort.findOrderResultById(query.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(OrderingError.ORDERING_ORDER_NOT_FOUND, Order.class, query.orderId()));
    }
}
