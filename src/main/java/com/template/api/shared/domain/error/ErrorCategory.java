package com.template.api.shared.domain.error;

public enum ErrorCategory {

    VALIDATION(false),
    NOT_FOUND(false),
    CONFLICT(false),
    UNAUTHENTICATED(false),
    FORBIDDEN(false),
    INTERNAL(true);

    private final boolean capturesDiagnostics;

    ErrorCategory(boolean capturesDiagnostics) {
        this.capturesDiagnostics = capturesDiagnostics;
    }

    public boolean capturesDiagnostics() {
        return capturesDiagnostics;
    }
}
