package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;
import com.template.api.shared.domain.error.ErrorCode;
import com.template.api.shared.domain.error.FieldViolation;

import java.util.List;

public class ValidationException extends BaseException {

    private final List<FieldViolation> violations;

    public ValidationException(ErrorCode errorCode, String message, List<FieldViolation> violations) {
        super(errorCode != null ? errorCode : CommonError.VALIDATION_FAILED, ErrorCategory.VALIDATION, message);
        this.violations = (violations == null || violations.isEmpty()) ? List.of() : List.copyOf(violations);
    }

    public ValidationException(String message, List<FieldViolation> violations) {
        this(CommonError.VALIDATION_FAILED, message, violations);
    }

    public ValidationException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ValidationException(String message) {
        this(CommonError.VALIDATION_FAILED, message, List.of());
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
