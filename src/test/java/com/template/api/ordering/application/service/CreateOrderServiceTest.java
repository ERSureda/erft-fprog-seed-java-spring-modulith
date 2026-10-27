package com.template.api.ordering.application.service;

import com.template.api.ordering.application.command.CreateOrderCommand;
import com.template.api.ordering.application.port.out.OrderRepositoryPort;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.domain.model.Order;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.ExecutionContextPort;
import com.template.api.shared.application.port.out.UtcClockPort;
import com.template.api.shared.application.port.out.UuidGeneratorPort;
import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderService Application Unit Tests")
class CreateOrderServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private ExecutionContextPort executionContextPort;

    @Mock
    private UuidGeneratorPort uuidGeneratorPort;

    @Mock
    private UtcClockPort utcClockPort;

    @InjectMocks
    private CreateOrderService createOrderService;

    @Test
    @DisplayName("Should create order and persist via repository port when command is valid")
    void should_create_order_and_persist_when_command_is_valid() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID generatedOrderId = UUID.randomUUID();
        Instant fixedNow = Instant.parse("2026-10-25T12:00:00Z");
        when(executionContextPort.current()).thenReturn(
                new ExecutionContext(tenantId, userId, Set.of("USER"))
        );
        when(uuidGeneratorPort.generateId()).thenReturn(generatedOrderId);
        when(utcClockPort.now()).thenReturn(fixedNow);

        CreateOrderCommand command = new CreateOrderCommand(null, new BigDecimal("150.00"), "EUR");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            assertThat(saved.getTenantId()).isEqualTo(tenantId);
            assertThat(saved.getCustomerId()).isEqualTo(userId);
            assertThat(saved.getCreatedAt()).isEqualTo(fixedNow);
            return saved;
        });

        OrderResult result = createOrderService.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(generatedOrderId);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.currency()).isEqualTo("EUR");
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should use command customer id when provided in command")
    void should_use_command_customer_id_when_command_customer_id_is_provided() {
        UUID tenantId = UUID.randomUUID();
        UUID contextUserId = UUID.randomUUID();
        UUID commandCustomerId = UUID.randomUUID();
        UUID generatedOrderId = UUID.randomUUID();
        Instant fixedNow = Instant.parse("2026-10-25T12:00:00Z");

        when(executionContextPort.current()).thenReturn(
                new ExecutionContext(tenantId, contextUserId, Set.of("USER"))
        );
        when(uuidGeneratorPort.generateId()).thenReturn(generatedOrderId);
        when(utcClockPort.now()).thenReturn(fixedNow);

        CreateOrderCommand command = new CreateOrderCommand(commandCustomerId, new BigDecimal("200.00"), "EUR");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            assertThat(saved.getTenantId()).isEqualTo(tenantId);
            assertThat(saved.getCustomerId()).isEqualTo(commandCustomerId);
            assertThat(saved.getCreatedAt()).isEqualTo(fixedNow);
            return saved;
        });

        OrderResult result = createOrderService.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(generatedOrderId);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should generate UUIDv7 for customer when command customer id and context user id are null")
    void should_generate_uuid_v7_for_customer_when_command_customer_id_and_context_user_id_are_null() {
        UUID tenantId = UUID.randomUUID();
        UUID generatedCustomerId = UUID.randomUUID();
        UUID generatedOrderId = UUID.randomUUID();
        Instant fixedNow = Instant.parse("2026-10-25T12:00:00Z");
        when(executionContextPort.current()).thenReturn(
                new ExecutionContext(tenantId, null, Set.of())
        );
        when(uuidGeneratorPort.generateId()).thenReturn(generatedCustomerId, generatedOrderId);
        when(utcClockPort.now()).thenReturn(fixedNow);

        CreateOrderCommand command = new CreateOrderCommand(null, new BigDecimal("99.00"), "EUR");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            assertThat(saved.getTenantId()).isEqualTo(tenantId);
            assertThat(saved.getCustomerId()).isEqualTo(generatedCustomerId);
            return saved;
        });

        OrderResult result = createOrderService.execute(command);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(generatedOrderId);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("Should throw ForbiddenException when context is null")
    void should_throw_forbidden_exception_when_context_is_null() {
        when(executionContextPort.current()).thenReturn(null);
        CreateOrderCommand command = new CreateOrderCommand(null, new BigDecimal("100.00"), "EUR");

        assertThatThrownBy(() -> createOrderService.execute(command))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Missing required tenant context for order creation");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when context has no tenant")
    void should_throw_forbidden_exception_when_context_has_no_tenant() {
        when(executionContextPort.current()).thenReturn(ExecutionContext.anonymous());
        CreateOrderCommand command = new CreateOrderCommand(null, new BigDecimal("100.00"), "EUR");

        assertThatThrownBy(() -> createOrderService.execute(command))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Missing required tenant context for order creation");
    }

    @Test
    @DisplayName("Should throw ValidationException when currency code is invalid")
    void should_throw_validation_exception_when_currency_code_is_invalid() {
        UUID tenantId = UUID.randomUUID();
        when(executionContextPort.current()).thenReturn(
                new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER"))
        );

        CreateOrderCommand command = new CreateOrderCommand(null, new BigDecimal("50.00"), "INVALID_CURRENCY");

        assertThatThrownBy(() -> createOrderService.execute(command))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ORDER_CURRENCY_INVALID_CODE");
    }
}
