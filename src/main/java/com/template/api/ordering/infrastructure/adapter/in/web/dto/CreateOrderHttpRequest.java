package com.template.api.ordering.infrastructure.adapter.in.web.dto;

import com.template.api.ordering.application.command.CreateOrderCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderHttpRequest(
        @NotNull(message = "ORDER_AMOUNT_REQUIRED")
        @DecimalMin(value = "0.01", message = "ORDER_AMOUNT_MIN_VALUE")
        BigDecimal amount,

        @NotBlank(message = "ORDER_CURRENCY_REQUIRED")
        @Size(min = 3, max = 3, message = "ORDER_CURRENCY_INVALID_LENGTH")
        @Pattern(regexp = "^[A-Z]{3}$", message = "ORDER_CURRENCY_INVALID_FORMAT")
        String currency
) {

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(this.amount, this.currency);
    }
}
