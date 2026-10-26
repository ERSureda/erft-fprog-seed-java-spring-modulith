package com.template.api.shared.infrastructure.adapter.out.context;

import com.template.api.shared.application.context.ExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionContextAdapter Unit Tests")
class ExecutionContextAdapterTest {

    private ExecutionContextAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ExecutionContextAdapter();
        ExecutionContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("should_return_anonymous_context_when_holder_is_empty")
    void should_return_anonymous_context_when_holder_is_empty() {
        ExecutionContext current = adapter.current();

        assertThat(current).isNotNull();
        assertThat(current).isSameAs(ExecutionContext.anonymous());
        assertThat(adapter.findCurrent()).isEmpty();
    }

    @Test
    @DisplayName("should_return_bound_context_when_holder_is_populated")
    void should_return_bound_context_when_holder_is_populated() {
        ExecutionContext expected = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), Set.of("USER"), "corr-1");
        ExecutionContextHolder.set(expected);

        ExecutionContext current = adapter.current();
        Optional<ExecutionContext> found = adapter.findCurrent();

        assertThat(current).isSameAs(expected);
        assertThat(found).containsSame(expected);
    }
}
