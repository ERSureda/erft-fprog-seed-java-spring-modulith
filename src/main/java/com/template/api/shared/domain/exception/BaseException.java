package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.ErrorCategory;
import com.template.api.shared.domain.error.ErrorCode;

import java.util.Objects;

public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorCategory category;

    protected BaseException(ErrorCode errorCode, ErrorCategory category, String message, Throwable cause) {
        super(
                (message != null && !message.isBlank()) ? message : Objects.requireNonNull(errorCode).code(),
                cause,
                true,
                Objects.requireNonNull(category, "category cannot be null").capturesDiagnostics()
        );
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode cannot be null");
        this.category = category;
    }

    protected BaseException(ErrorCode errorCode, ErrorCategory category, String message) {
        this(errorCode, category, message, null);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorCategory category() {
        return category;
    }
}
