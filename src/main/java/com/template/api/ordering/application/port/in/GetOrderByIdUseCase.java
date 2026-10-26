package com.template.api.ordering.application.port.in;

import com.template.api.ordering.application.query.GetOrderByIdQuery;
import com.template.api.ordering.application.result.OrderResult;

public interface GetOrderByIdUseCase {

    OrderResult execute(GetOrderByIdQuery query);
}
