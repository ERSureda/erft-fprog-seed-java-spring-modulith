package com.template.api.shared.domain.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BaseException Unit Tests (ERR-01, ERR-02)")
class BaseExceptionTest {

    @Test
    @DisplayName("Should disable stack trace generation when category does not capture diagnostics")
    void should_disable_stack_trace_when_category_does_not_capture_diagnostics() {
        BaseException exception = new ConflictException(CommonError.RESOURCE_CONFLICT, "Business conflict");

        assertThat(exception.category().capturesDiagnostics()).isFalse();
        assertThat(exception.getStackTrace()).isEmpty();
        assertThat(exception.errorCode()).isEqualTo(CommonError.RESOURCE_CONFLICT);
        assertThat(exception.getMessage()).isEqualTo("Business conflict");
    }

    @Test
    @DisplayName("Should retain stack trace and cause when category captures diagnostics")
    void should_retain_stack_trace_and_cause_when_category_captures_diagnostics() {
        RuntimeException rootCause = new RuntimeException("Socket timeout");
        BaseException exception = new InfrastructureException("Database failure", rootCause);

        assertThat(exception.category().capturesDiagnostics()).isTrue();
        assertThat(exception.getStackTrace()).isNotEmpty();
        assertThat(exception.getCause()).isSameAs(rootCause);
        assertThat(exception.errorCode()).isEqualTo(CommonError.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("Should fallback to errorCode when message is null or blank")
    void should_fallback_to_error_code_when_message_is_null_or_blank() {
        BaseException exception = new ConflictException(CommonError.RESOURCE_CONFLICT);

        assertThat(exception.getMessage()).isEqualTo(CommonError.RESOURCE_CONFLICT.code());
    }

    @Test
    @DisplayName("Should reject null errorCode or null category")
    void should_reject_null_error_code_or_null_category() {
        assertThatThrownBy(() -> new BaseException(null, ErrorCategory.VALIDATION, "msg") {})
                .isInstanceOf(NullPointerException.class)
                .hasMessage("BASE_EXCEPTION_ERROR_CODE_CANNOT_BE_NULL");

        assertThatThrownBy(() -> new BaseException(CommonError.VALIDATION_FAILED, null, "msg") {})
                .isInstanceOf(NullPointerException.class)
                .hasMessage("BASE_EXCEPTION_CATEGORY_CANNOT_BE_NULL");
    }
}
