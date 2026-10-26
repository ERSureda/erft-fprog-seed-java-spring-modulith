package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;

public class UnauthenticatedException extends BaseException {

    public UnauthenticatedException(String message) {
        super(CommonError.UNAUTHENTICATED, ErrorCategory.UNAUTHENTICATED, message);
    }
}
