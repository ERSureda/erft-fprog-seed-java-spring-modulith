package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;

public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(CommonError.FORBIDDEN, ErrorCategory.FORBIDDEN, message);
    }
}
