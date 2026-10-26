package com.template.api.ordering.application.port.in;

import com.template.api.ordering.application.command.ProcessOrderPaymentCommand;

public interface ProcessOrderPaymentUseCase {

    void execute(ProcessOrderPaymentCommand command);
}
