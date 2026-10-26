package com.template.api.ordering.infrastructure.adapter.in.web.dto;

import com.template.api.ordering.application.command.CreateOrderCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CreateOrderHttpRequest Unit Tests")
class CreateOrderHttpRequestTest {

    @Test
    @DisplayName("toCommand should map all fields directly into CreateOrderCommand with zero overhead")
    void should_map_fields_directly_when_to_command_invoked() {
        BigDecimal amount = new BigDecimal("149.99");
        String currency = "EUR";
        CreateOrderHttpRequest request = new CreateOrderHttpRequest(amount, currency);

        CreateOrderCommand command = request.toCommand();

        assertThat(command).isNotNull();
        assertThat(command.amount()).isEqualByComparingTo(amount);
        assertThat(command.currency()).isEqualTo(currency);
    }
}
