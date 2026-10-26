package com.template.api.ordering.application.service;

import com.template.api.ordering.application.port.out.OrderQueryPort;
import com.template.api.ordering.application.query.GetOrderByIdQuery;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.domain.OrderingError;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.ExecutionContextPort;
import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetOrderByIdService Application Unit Tests")
class GetOrderByIdServiceTest {

    @Mock
    private OrderQueryPort orderQueryPort;

    @Mock
    private ExecutionContextPort executionContextPort;

    @InjectMocks
    private GetOrderByIdService getOrderByIdService;

    @Test
    @DisplayName("should_return_order_result_when_order_exists")
    void should_return_order_result_when_order_exists() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrderResult expected = new OrderResult(orderId, "PENDING", new BigDecimal("89.99"), "EUR");
        when(executionContextPort.current()).thenReturn(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        when(orderQueryPort.findOrderResultById(orderId)).thenReturn(Optional.of(expected));

        OrderResult actual = getOrderByIdService.execute(new GetOrderByIdQuery(orderId));

        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo(orderId);
        assertThat(actual.status()).isEqualTo("PENDING");
        assertThat(actual.amount()).isEqualByComparingTo(new BigDecimal("89.99"));
    }

    @Test
    @DisplayName("should_throw_resource_not_found_exception_when_order_not_found")
    void should_throw_resource_not_found_exception_when_order_not_found() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(executionContextPort.current()).thenReturn(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        when(orderQueryPort.findOrderResultById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getOrderByIdService.execute(new GetOrderByIdQuery(orderId)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(orderId.toString())
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).errorCode())
                        .isEqualTo(OrderingError.ORDERING_ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("should_throw_forbidden_exception_when_tenant_context_is_missing")
    void should_throw_forbidden_exception_when_tenant_context_is_missing() {
        UUID orderId = UUID.randomUUID();
        when(executionContextPort.current()).thenReturn(ExecutionContext.anonymous());

        assertThatThrownBy(() -> getOrderByIdService.execute(new GetOrderByIdQuery(orderId)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Missing required tenant context for order query");
    }
}
