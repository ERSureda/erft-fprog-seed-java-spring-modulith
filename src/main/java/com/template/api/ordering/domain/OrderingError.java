package com.template.api.ordering.domain;

import com.template.api.shared.domain.error.ErrorCode;

public enum OrderingError implements ErrorCode {

    ORDERING_ORDER_INVALID_STATUS,
    ORDERING_ORDER_NOT_CONFIRMED,
    ORDERING_ORDER_ALREADY_SHIPPED,
    ORDERING_ORDER_NOT_FOUND,
    ORDERING_STOCK_INSUFFICIENT,
    ORDERING_PAYMENT_GATEWAY_TIMEOUT;

    @Override
    public String code() {
        return name();
    }
}
