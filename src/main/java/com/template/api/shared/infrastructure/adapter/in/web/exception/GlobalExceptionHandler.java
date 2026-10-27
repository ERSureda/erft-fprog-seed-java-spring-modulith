package com.template.api.shared.infrastructure.adapter.in.web.exception;

import com.template.api.shared.domain.error.CommonError;
import com.template.api.shared.domain.error.ErrorCategory;
import com.template.api.shared.domain.error.FieldViolation;
import com.template.api.shared.domain.exception.BaseException;
import com.template.api.shared.domain.exception.ValidationException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INTERNAL_ERROR_MSG = "An unexpected error occurred";
    private static final String VALIDATION_FAILED_MSG = "Validation failed for one or more fields";

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex) {
        HttpStatus status = toHttpStatus(ex.category());
        String code = ex.errorCode().code();

        if (ex.category().capturesDiagnostics()) {
            log.error("Internal exception [{}] - {}", code, ex.getMessage(), ex);
            return ResponseEntity.status(status).body(new ErrorResponse(status.value(), code, INTERNAL_ERROR_MSG));
        }

        log.warn("Business rule violation [{}] - {}", code, ex.getMessage());
        List<FieldViolation> violations = (ex instanceof ValidationException ve) ? ve.violations() : List.of();
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), code, ex.getMessage(), violations));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldError)
                .toList();

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                CommonError.VALIDATION_FAILED.code(),
                VALIDATION_FAILED_MSG,
                violations
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();

        return ResponseEntity.badRequest().body(new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                CommonError.VALIDATION_FAILED.code(),
                VALIDATION_FAILED_MSG,
                violations
        ));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorResponse> handleUnhandled(Throwable ex) {
        log.error("Unhandled runtime exception", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                CommonError.INTERNAL_SERVER_ERROR.code(),
                INTERNAL_ERROR_MSG
        ));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request
    ) {
        if (body instanceof ErrorResponse) {
            return ResponseEntity.status(statusCode).headers(headers).body(body);
        }

        String detail = (body instanceof ProblemDetail pd && pd.getDetail() != null)
                ? pd.getDetail()
                : statusCode.toString();

        return ResponseEntity.status(statusCode).headers(headers).body(
                new ErrorResponse(statusCode.value(), resolveErrorCode(statusCode), detail)
        );
    }

    private static String resolveErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 404 -> CommonError.RESOURCE_NOT_FOUND.code();
            case 401 -> CommonError.UNAUTHENTICATED.code();
            case 403 -> CommonError.FORBIDDEN.code();
            case 409 -> CommonError.RESOURCE_CONFLICT.code();
            default -> statusCode.is4xxClientError()
                    ? CommonError.VALIDATION_FAILED.code()
                    : CommonError.INTERNAL_SERVER_ERROR.code();
        };
    }

    private FieldViolation mapFieldError(FieldError error) {
        String msg = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
        return new FieldViolation(error.getField(), msg);
    }

    private static HttpStatus toHttpStatus(ErrorCategory category) {
        return switch (category) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
