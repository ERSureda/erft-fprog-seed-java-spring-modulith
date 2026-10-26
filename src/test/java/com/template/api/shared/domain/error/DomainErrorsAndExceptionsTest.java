package com.template.api.shared.domain.error;

import com.template.api.shared.domain.exception.BaseException;
import com.template.api.shared.domain.exception.ConflictException;
import com.template.api.shared.domain.exception.ExternalServiceException;
import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
import com.template.api.shared.domain.exception.UnauthenticatedException;
import com.template.api.shared.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Domain Errors and Exceptions Unit Tests")
class DomainErrorsAndExceptionsTest {

    @ParameterizedTest
    @EnumSource(CommonError.class)
    @DisplayName("CommonError codes should match enum constant names")
    void should_match_enum_name_when_common_error_code_inspected(CommonError error) {
        assertThat(error.code()).isEqualTo(error.name());
    }

    @ParameterizedTest
    @EnumSource(CommonError.class)
    @DisplayName("CommonError codes should adhere to standard UPPER_SNAKE_CASE pattern")
    void should_follow_standard_pattern_when_common_error_code_inspected(CommonError error) {
        assertThat(error.code())
                .doesNotStartWith("GLOBAL_")
                .matches("^[A-Z]+(_[A-Z]+)*$");
    }

    @Test
    @DisplayName("ErrorCategory should correctly flag diagnostic capture")
    void should_flag_diagnostic_capture_when_category_inspected() {
        assertThat(ErrorCategory.VALIDATION.capturesDiagnostics()).isFalse();
        assertThat(ErrorCategory.NOT_FOUND.capturesDiagnostics()).isFalse();
        assertThat(ErrorCategory.CONFLICT.capturesDiagnostics()).isFalse();
        assertThat(ErrorCategory.UNAUTHENTICATED.capturesDiagnostics()).isFalse();
        assertThat(ErrorCategory.FORBIDDEN.capturesDiagnostics()).isFalse();
        assertThat(ErrorCategory.INTERNAL.capturesDiagnostics()).isTrue();
    }

    @Test
    @DisplayName("FieldViolation record should store and expose field and message")
    void should_expose_attributes_when_field_violation_created() {
        FieldViolation violation = new FieldViolation("email", "must be a valid email address");

        assertThat(violation.field()).isEqualTo("email");
        assertThat(violation.message()).isEqualTo("must be a valid email address");
    }

    @Test
    @DisplayName("Business exceptions should suppress stack traces for zero overhead")
    void should_suppress_stack_trace_when_business_exceptions_thrown() {
        ResourceNotFoundException notFound = new ResourceNotFoundException("Not found");
        ConflictException conflict = new ConflictException("Conflict");
        ValidationException validation = new ValidationException("Invalid");
        ForbiddenException forbidden = new ForbiddenException("Forbidden");
        UnauthenticatedException unauthenticated = new UnauthenticatedException("Unauthenticated");

        assertThat(notFound.getStackTrace()).isEmpty();
        assertThat(notFound.category()).isEqualTo(ErrorCategory.NOT_FOUND);
        assertThat(notFound.errorCode()).isEqualTo(CommonError.RESOURCE_NOT_FOUND);

        assertThat(conflict.getStackTrace()).isEmpty();
        assertThat(conflict.category()).isEqualTo(ErrorCategory.CONFLICT);

        assertThat(validation.getStackTrace()).isEmpty();
        assertThat(validation.category()).isEqualTo(ErrorCategory.VALIDATION);

        assertThat(forbidden.getStackTrace()).isEmpty();
        assertThat(forbidden.category()).isEqualTo(ErrorCategory.FORBIDDEN);

        assertThat(unauthenticated.getStackTrace()).isEmpty();
        assertThat(unauthenticated.category()).isEqualTo(ErrorCategory.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Infrastructure and External exceptions should retain diagnostic stack traces")
    void should_capture_stack_trace_when_technical_exceptions_thrown() {
        InfrastructureException infra = new InfrastructureException("DB down");
        ExternalServiceException ext = new ExternalServiceException(CommonError.INTERNAL_SERVER_ERROR, "HTTP timeout", new RuntimeException());

        assertThat(infra.getStackTrace()).isNotEmpty();
        assertThat(infra.category()).isEqualTo(ErrorCategory.INTERNAL);
        assertThat(infra.errorCode()).isEqualTo(CommonError.INTERNAL_SERVER_ERROR);

        assertThat(ext.getStackTrace()).isNotEmpty();
        assertThat(ext.category()).isEqualTo(ErrorCategory.INTERNAL);
    }

    @Test
    @DisplayName("ValidationException should expose field violations defensively copied")
    void should_handle_violations_defensively_when_validation_exception_created() {
        FieldViolation v1 = new FieldViolation("field1", "error1");
        FieldViolation v2 = new FieldViolation("field2", "error2");

        ValidationException ve = new ValidationException("Validation failed", List.of(v1, v2));

        assertThat(ve.violations()).hasSize(2).containsExactly(v1, v2);

        ValidationException emptyVe = new ValidationException("Validation failed");
        assertThat(emptyVe.violations()).isEmpty();
    }

    @Test
    @DisplayName("ResourceNotFoundException should format entity class and id properly")
    void should_format_entity_and_id_when_resource_not_found_exception_created() {
        ResourceNotFoundException ex = new ResourceNotFoundException(String.class, "abc-123");

        assertThat(ex.getMessage()).isEqualTo("Resource 'String' with id 'abc-123' not found");
        assertThat(ex.category()).isEqualTo(ErrorCategory.NOT_FOUND);
    }

    @Test
    @DisplayName("BaseException constructor should fallback to errorCode code when message is blank")
    void should_fallback_to_error_code_when_message_is_blank() {
        BaseException ex = new ConflictException(CommonError.RESOURCE_CONFLICT, "   ");

        assertThat(ex.getMessage()).isEqualTo(CommonError.RESOURCE_CONFLICT.code());
    }

    @Test
    @DisplayName("BaseException should reject null category and null errorCode")
    void should_reject_null_category_or_error_code_when_base_exception_created() {
        assertThatThrownBy(() -> new BaseException(null, ErrorCategory.CONFLICT, "msg") {})
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new BaseException(CommonError.RESOURCE_CONFLICT, null, "msg") {})
                .isInstanceOf(NullPointerException.class);
    }
}
