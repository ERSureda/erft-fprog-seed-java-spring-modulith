package com.template.api.ordering.application.service;

import com.template.api.ordering.application.command.ProcessOrderPaymentCommand;
import com.template.api.ordering.application.port.in.ProcessOrderPaymentUseCase;
import com.template.api.ordering.application.port.out.OrderRepositoryPort;
import com.template.api.ordering.domain.OrderingError;
import com.template.api.ordering.domain.model.Order;
import com.template.api.shared.application.port.out.UtcClockPort;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProcessOrderPaymentService implements ProcessOrderPaymentUseCase {

    private final OrderRepositoryPort orderRepository;
    private final UtcClockPort utcClockPort;

    @Override
    @Transactional
    public void execute(ProcessOrderPaymentCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        OrderingError.ORDERING_ORDER_NOT_FOUND,
                        Order.class,
                        command.orderId()
                ));

        if ("CONFIRMED".equalsIgnoreCase(command.paymentStatus())) {
            Instant now = utcClockPort.now();
            order.confirm(now);
            orderRepository.save(order);
        }
    }
}
