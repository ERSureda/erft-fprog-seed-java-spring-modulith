package com.template.api.shared.application.port.out;

import com.template.api.shared.application.context.ExecutionContext;

import java.util.Optional;

public interface ExecutionContextPort {

    ExecutionContext current();

    Optional<ExecutionContext> findCurrent();
}
