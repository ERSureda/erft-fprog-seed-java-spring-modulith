package com.template.api.shared.infrastructure.adapter.in.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.template.api.shared.domain.error.FieldViolation;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String code,
        String detail,
        List<FieldViolation> errors
) {

    public ErrorResponse(int status, String code, String detail) {
        this(status, code, detail, List.of());
    }
}
