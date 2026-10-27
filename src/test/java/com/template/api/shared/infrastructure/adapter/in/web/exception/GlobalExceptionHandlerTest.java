package com.template.api.shared.infrastructure.adapter.in.web.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.FieldViolation;
import com.template.api.shared.domain.exception.ConflictException;
import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
import com.template.api.shared.domain.exception.UnauthenticatedException;
import com.template.api.shared.domain.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private static final Method DUMMY_METHOD;

    static {
        try {
            DUMMY_METHOD = DummyController.class.getDeclaredMethod("dummyMethod", String.class);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("1. BaseException and Business Subclasses Handling")
    class BaseExceptionTests {

        @Test
        @DisplayName("Should transform ResourceNotFoundException to ErrorResponse 404")
        void should_transform_resource_not_found_exception_when_handled() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Customer with ID 123 not found");

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(404);
            assertThat(response.getBody().code()).isEqualTo(CommonError.RESOURCE_NOT_FOUND.code());
            assertThat(response.getBody().detail()).isEqualTo("Customer with ID 123 not found");
            assertThat(response.getBody().errors()).isEmpty();
        }

        @Test
        @DisplayName("Should transform ConflictException to ErrorResponse 409")
        void should_transform_conflict_exception_when_handled() {
            ConflictException ex = new ConflictException("Email already registered");

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(409);
            assertThat(response.getBody().code()).isEqualTo(CommonError.RESOURCE_CONFLICT.code());
            assertThat(response.getBody().detail()).isEqualTo("Email already registered");
            assertThat(response.getBody().errors()).isEmpty();
        }

        @Test
        @DisplayName("Should transform simple ValidationException to ErrorResponse 400 without violations")
        void should_transform_simple_validation_exception_when_handled() {
            ValidationException ex = new ValidationException("Invalid price amount");

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().code()).isEqualTo(CommonError.VALIDATION_FAILED.code());
            assertThat(response.getBody().detail()).isEqualTo("Invalid price amount");
            assertThat(response.getBody().errors()).isEmpty();
        }

        @Test
        @DisplayName("Should transform ValidationException with violations to ErrorResponse 400 with errors populated")
        void should_transform_validation_exception_with_violations_when_handled() {
            List<FieldViolation> violations = List.of(
                    new FieldViolation("username", "Username already taken"),
                    new FieldViolation("age", "Age must be at least 18")
            );
            ValidationException ex = new ValidationException("Command validation failed", violations);

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().errors()).hasSize(2);
            assertThat(response.getBody().errors().get(0).field()).isEqualTo("username");
            assertThat(response.getBody().errors().get(0).message()).isEqualTo("Username already taken");
            assertThat(response.getBody().errors().get(1).field()).isEqualTo("age");
            assertThat(response.getBody().errors().get(1).message()).isEqualTo("Age must be at least 18");
        }

        @Test
        @DisplayName("Should transform ForbiddenException to ErrorResponse 403")
        void should_transform_forbidden_exception_when_handled() {
            ForbiddenException ex = new ForbiddenException("Access denied to tenant");

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(403);
            assertThat(response.getBody().code()).isEqualTo(CommonError.FORBIDDEN.code());
        }

        @Test
        @DisplayName("Should transform UnauthenticatedException to ErrorResponse 401")
        void should_transform_unauthenticated_exception_when_handled() {
            UnauthenticatedException ex = new UnauthenticatedException("Token expired");

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(401);
            assertThat(response.getBody().code()).isEqualTo(CommonError.UNAUTHENTICATED.code());
        }

        @Test
        @DisplayName("Should mask details for INTERNAL category exceptions (InfrastructureException)")
        void should_mask_internal_exceptions_when_handled() {
            InfrastructureException ex = new InfrastructureException("Database connection timeout on pool");

            ResponseEntity<ErrorResponse> response = handler.handleBaseException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(500);
            assertThat(response.getBody().code()).isEqualTo(CommonError.INTERNAL_SERVER_ERROR.code());
            assertThat(response.getBody().detail()).isEqualTo("An unexpected error occurred");
            assertThat(response.getBody().errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("2. Bean Validation and Constraint Violations")
    class ValidationHandlingTests {

        @Test
        @DisplayName("Should transform MethodArgumentNotValidException into ErrorResponse 400 with FieldViolations")
        void should_transform_method_argument_not_valid_when_handled() {
            MethodParameter parameter = new MethodParameter(DUMMY_METHOD, 0);

            DummyDto target = new DummyDto(null, -5);
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "dummyDto");
            bindingResult.addError(new FieldError("dummyDto", "email", null, false, null, null, "Email is mandatory"));
            bindingResult.addError(new FieldError("dummyDto", "amount", -5, false, null, null, "Amount must be positive"));

            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

            ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                    ex,
                    new HttpHeaders(),
                    HttpStatus.BAD_REQUEST,
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(400);
            assertThat(errorResponse.code()).isEqualTo(CommonError.VALIDATION_FAILED.code());
            assertThat(errorResponse.detail()).isEqualTo("Validation failed for one or more fields");
            assertThat(errorResponse.errors()).hasSize(2);

            FieldViolation emailError = errorResponse.errors().get(0);
            assertThat(emailError.field()).isEqualTo("email");
            assertThat(emailError.message()).isEqualTo("Email is mandatory");

            FieldViolation amountError = errorResponse.errors().get(1);
            assertThat(amountError.field()).isEqualTo("amount");
            assertThat(amountError.message()).isEqualTo("Amount must be positive");
        }

        @Test
        @DisplayName("Should transform ConstraintViolationException into ErrorResponse 400 with FieldViolations")
        void should_transform_constraint_violation_exception_when_handled() {
            @SuppressWarnings("unchecked")
            ConstraintViolation<Object> violation = Mockito.mock(ConstraintViolation.class);
            Path mockPath = Mockito.mock(Path.class);
            given(mockPath.toString()).willReturn("user.name");
            given(violation.getPropertyPath()).willReturn(mockPath);
            given(violation.getMessage()).willReturn("must not be blank");

            ConstraintViolationException ex = new ConstraintViolationException("Violations occurred", Set.of(violation));

            ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(400);
            assertThat(response.getBody().code()).isEqualTo(CommonError.VALIDATION_FAILED.code());
            assertThat(response.getBody().detail()).isEqualTo("Validation failed for one or more fields");
            assertThat(response.getBody().errors()).hasSize(1);
            assertThat(response.getBody().errors().get(0).field()).isEqualTo("user.name");
            assertThat(response.getBody().errors().get(0).message()).isEqualTo("must not be blank");
        }
    }

    @Nested
    @DisplayName("3. Unhandled Fallback (handleUnhandled)")
    class CatchAllTests {

        @Test
        @DisplayName("Should intercept unexpected Throwable and return masked 500 ErrorResponse")
        void should_return_masked_500_when_unexpected_throwable_handled() {
            NullPointerException ex = new NullPointerException("Null pointer at Line 42");

            ResponseEntity<ErrorResponse> response = handler.handleUnhandled(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(500);
            assertThat(response.getBody().code()).isEqualTo(CommonError.INTERNAL_SERVER_ERROR.code());
            assertThat(response.getBody().detail()).isEqualTo("An unexpected error occurred");
            assertThat(response.getBody().errors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("4. Framework ResponseEntity Conversion (createResponseEntity)")
    class FrameworkHandlerTests {

        @Test
        @DisplayName("Should pass through ErrorResponse bodies unmodified")
        void should_pass_through_error_response_body_when_already_error_response() {
            ErrorResponse original = new ErrorResponse(400, "CUSTOM_CODE", "Detail message");

            ResponseEntity<Object> response = handler.createResponseEntity(
                    original,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(400),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isSameAs(original);
        }

        @Test
        @DisplayName("Should convert Spring ProblemDetail to ErrorResponse")
        void should_convert_problem_detail_to_error_response_when_handled() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND,
                    "No endpoint GET /api/v1/unknown found"
            );

            ResponseEntity<Object> response = handler.createResponseEntity(
                    problem,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(404),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(404);
            assertThat(errorResponse.code()).isEqualTo(CommonError.RESOURCE_NOT_FOUND.code());
            assertThat(errorResponse.detail()).isEqualTo("No endpoint GET /api/v1/unknown found");
            assertThat(errorResponse.errors()).isEmpty();
        }

        @Test
        @DisplayName("Should fallback to status code string when body has no detail")
        void should_fallback_to_status_code_string_when_body_has_no_detail() {
            ResponseEntity<Object> response = handler.createResponseEntity(
                    "Raw string error",
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(503),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(503);
            assertThat(errorResponse.code()).isEqualTo(CommonError.INTERNAL_SERVER_ERROR.code());
            assertThat(errorResponse.detail()).isEqualTo("503 SERVICE_UNAVAILABLE");
        }

        @Test
        @DisplayName("Should convert 400 Client Error to ErrorResponse with VALIDATION_FAILED")
        void should_convert_to_validation_failed_code_when_400_problem_detail_handled() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Malformed JSON request body"
            );

            ResponseEntity<Object> response = handler.createResponseEntity(
                    problem,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(400),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(400);
            assertThat(errorResponse.code()).isEqualTo(CommonError.VALIDATION_FAILED.code());
            assertThat(errorResponse.detail()).isEqualTo("Malformed JSON request body");
            assertThat(errorResponse.errors()).isEmpty();
        }

        @Test
        @DisplayName("Should convert 401 Client Error to ErrorResponse with UNAUTHENTICATED")
        void should_convert_to_unauthenticated_code_when_401_problem_detail_handled() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication credentials required"
            );

            ResponseEntity<Object> response = handler.createResponseEntity(
                    problem,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(401),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(401);
            assertThat(errorResponse.code()).isEqualTo(CommonError.UNAUTHENTICATED.code());
            assertThat(errorResponse.detail()).isEqualTo("Authentication credentials required");
            assertThat(errorResponse.errors()).isEmpty();
        }

        @Test
        @DisplayName("Should convert 403 Client Error to ErrorResponse with FORBIDDEN")
        void should_convert_to_forbidden_code_when_403_problem_detail_handled() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN,
                    "Access denied to resource"
            );

            ResponseEntity<Object> response = handler.createResponseEntity(
                    problem,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(403),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(403);
            assertThat(errorResponse.code()).isEqualTo(CommonError.FORBIDDEN.code());
            assertThat(errorResponse.detail()).isEqualTo("Access denied to resource");
            assertThat(errorResponse.errors()).isEmpty();
        }

        @Test
        @DisplayName("Should convert 500 Server Error to ErrorResponse with INTERNAL_SERVER_ERROR")
        void should_convert_to_internal_server_error_code_when_500_problem_detail_handled() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred"
            );

            ResponseEntity<Object> response = handler.createResponseEntity(
                    problem,
                    new HttpHeaders(),
                    HttpStatusCode.valueOf(500),
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);

            ErrorResponse errorResponse = (ErrorResponse) response.getBody();
            assertThat(errorResponse.status()).isEqualTo(500);
            assertThat(errorResponse.code()).isEqualTo(CommonError.INTERNAL_SERVER_ERROR.code());
            assertThat(errorResponse.detail()).isEqualTo("An unexpected error occurred");
            assertThat(errorResponse.errors()).isEmpty();
        }
    }

    private static class DummyController {
        @SuppressWarnings("unused")
        void dummyMethod(String input) {}
    }

    private record DummyDto(String email, Integer amount) {}
}
