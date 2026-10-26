package com.template.api.ordering.infrastructure.adapter.in.web;

import com.template.api.ordering.application.port.in.CreateOrderUseCase;
import com.template.api.ordering.application.port.in.GetOrderByIdUseCase;
import com.template.api.ordering.application.query.GetOrderByIdQuery;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.infrastructure.adapter.in.web.dto.CreateOrderHttpRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderByIdUseCase getOrderByIdUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResult createOrder(@Valid @RequestBody CreateOrderHttpRequest request) {
        return createOrderUseCase.execute(request.toCommand());
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public OrderResult getOrderById(@PathVariable UUID id) {
        return getOrderByIdUseCase.execute(new GetOrderByIdQuery(id));
    }
}
