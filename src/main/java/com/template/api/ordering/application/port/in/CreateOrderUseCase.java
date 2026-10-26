package com.template.api.ordering.application.port.in;

import com.template.api.ordering.application.command.CreateOrderCommand;
import com.template.api.ordering.application.result.OrderResult;

public interface CreateOrderUseCase {

    OrderResult execute(CreateOrderCommand command);
}
