package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;
import com.template.api.shared.domain.error.ErrorCode;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(CommonError.RESOURCE_NOT_FOUND, ErrorCategory.NOT_FOUND, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode != null ? errorCode : CommonError.RESOURCE_NOT_FOUND, ErrorCategory.NOT_FOUND, message);
    }

    public ResourceNotFoundException(Class<?> entityClass, Object id) {
        this(CommonError.RESOURCE_NOT_FOUND, entityClass, id);
    }

    public ResourceNotFoundException(ErrorCode errorCode, Class<?> entityClass, Object id) {
        super(errorCode != null ? errorCode : CommonError.RESOURCE_NOT_FOUND,
                ErrorCategory.NOT_FOUND,
                "Resource '%s' with id '%s' not found".formatted(
                        entityClass != null ? entityClass.getSimpleName() : "Entity", id));
    }
}
