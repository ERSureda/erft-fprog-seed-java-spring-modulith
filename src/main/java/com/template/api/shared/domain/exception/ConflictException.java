package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;
import com.template.api.shared.domain.error.ErrorCode;

public class ConflictException extends BaseException {

    public ConflictException(String message) {
        super(CommonError.RESOURCE_CONFLICT, ErrorCategory.CONFLICT, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, ErrorCategory.CONFLICT, message);
    }

    public ConflictException(ErrorCode errorCode) {
        super(errorCode, ErrorCategory.CONFLICT, null);
    }
}
