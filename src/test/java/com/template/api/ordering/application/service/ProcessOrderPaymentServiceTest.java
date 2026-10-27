package com.template.api.ordering.application.service;

import com.template.api.ordering.application.command.ProcessOrderPaymentCommand;
import com.template.api.ordering.application.port.out.OrderRepositoryPort;
import com.template.api.ordering.domain.OrderingError;
import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.ordering.fixture.OrderFixture;
import com.template.api.shared.application.port.out.UtcClockPort;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
import com.template.api.shared.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessOrderPaymentService Application Unit Tests")
class ProcessOrderPaymentServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private UtcClockPort utcClockPort;

    @InjectMocks
    private ProcessOrderPaymentService processOrderPaymentService;

    @Test
    @DisplayName("Should confirm order and save when payment status is confirmed")
    void should_confirm_order_when_payment_status_is_confirmed() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant fixedNow = Instant.parse("2026-10-25T15:30:00Z");
        Order order = OrderFixture.createPendingOrder(orderId, tenantId, customerId, Money.of(new BigDecimal("120.00"), Currency.getInstance("EUR")));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(utcClockPort.now()).thenReturn(fixedNow);

        processOrderPaymentService.execute(new ProcessOrderPaymentCommand(orderId, "CONFIRMED"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getUpdatedAt()).isEqualTo(fixedNow);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when order is not found")
    void should_throw_resource_not_found_exception_when_order_not_found() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processOrderPaymentService.execute(new ProcessOrderPaymentCommand(orderId, "CONFIRMED")))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).errorCode())
                        .isEqualTo(OrderingError.ORDERING_ORDER_NOT_FOUND));

        verify(orderRepository, never()).save(any());
    }
}
