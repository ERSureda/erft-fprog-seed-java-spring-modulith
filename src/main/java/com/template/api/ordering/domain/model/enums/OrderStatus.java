package com.template.api.ordering.domain.model.enums;

public enum OrderStatus {

    PENDING,
    CONFIRMED,
    SHIPPED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SHIPPED || this == CANCELLED;
    }
}
