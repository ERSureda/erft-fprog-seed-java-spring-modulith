package com.template.api.ordering.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProcessOrderPaymentCommand Unit Tests")
class ProcessOrderPaymentCommandTest {

    @Test
    @DisplayName("should create command when valid parameters are provided")
    void should_create_command_when_valid_parameters_provided() {
        UUID orderId = UUID.randomUUID();
        String status = "CONFIRMED";

        ProcessOrderPaymentCommand command = new ProcessOrderPaymentCommand(orderId, status);

        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.paymentStatus()).isEqualTo(status);
    }

    @Test
    @DisplayName("should throw NullPointerException when orderId is null")
    void should_throw_null_pointer_exception_when_order_id_is_null() {
        assertThatThrownBy(() -> new ProcessOrderPaymentCommand(null, "CONFIRMED"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ORDER_ID_CANNOT_BE_NULL");
    }

    @Test
    @DisplayName("should throw NullPointerException when paymentStatus is null")
    void should_throw_null_pointer_exception_when_payment_status_is_null() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new ProcessOrderPaymentCommand(orderId, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("ORDER_PAYMENT_STATUS_CANNOT_BE_NULL");
    }
}
