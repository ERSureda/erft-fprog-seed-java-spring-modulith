package com.template.api.shared.domain.error;

public enum CommonError implements ErrorCode {

    VALIDATION_FAILED,
    RESOURCE_NOT_FOUND,
    RESOURCE_CONFLICT,
    UNAUTHENTICATED,
    FORBIDDEN,
    INTERNAL_SERVER_ERROR;

    @Override
    public String code() {
        return name();
    }
}
