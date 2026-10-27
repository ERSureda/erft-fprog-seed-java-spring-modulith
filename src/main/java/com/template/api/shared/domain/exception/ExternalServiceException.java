package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;
import com.template.api.shared.domain.error.ErrorCode;

public class ExternalServiceException extends BaseException {

    public ExternalServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, ErrorCategory.INTERNAL, message, cause);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(CommonError.INTERNAL_SERVER_ERROR, ErrorCategory.INTERNAL, message, cause);
    }

    public ExternalServiceException(ErrorCode errorCode, String message) {
        super(errorCode, ErrorCategory.INTERNAL, message, null);
    }
}
