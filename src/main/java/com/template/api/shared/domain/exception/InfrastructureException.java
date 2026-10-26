package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;

public class InfrastructureException extends BaseException {

    public InfrastructureException(String message, Throwable cause) {
        super(CommonError.INTERNAL_SERVER_ERROR, ErrorCategory.INTERNAL, message, cause);
    }

    public InfrastructureException(String message) {
        this(message, null);
    }
}
