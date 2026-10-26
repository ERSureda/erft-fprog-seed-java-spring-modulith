package com.template.api.shared.infrastructure.adapter.out.context;

import com.template.api.shared.application.context.ExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionContextHolder Unit Tests")
class ExecutionContextHolderTest {

    @BeforeEach
    @AfterEach
    void cleanUp() {
        ExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("Should set and retrieve context on the same thread")
    void should_return_context_when_set_and_get_invoked() {
        ExecutionContext context = ExecutionContext.anonymous();

        ExecutionContextHolder.set(context);

        assertThat(ExecutionContextHolder.get()).isSameAs(context);
    }

    @Test
    @DisplayName("Should clear context when setting null or calling clear")
    void should_remove_context_when_cleared_or_set_to_null() {
        ExecutionContext context = ExecutionContext.anonymous();

        ExecutionContextHolder.set(context);
        assertThat(ExecutionContextHolder.get()).isNotNull();

        ExecutionContextHolder.set(null);
        assertThat(ExecutionContextHolder.get()).isNull();

        ExecutionContextHolder.set(context);
        ExecutionContextHolder.clear();
        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Should maintain separate contexts across different threads")
    void should_isolate_contexts_when_accessed_across_different_threads() throws InterruptedException {
        ExecutionContext mainContext = ExecutionContext.anonymous();
        ExecutionContextHolder.set(mainContext);

        AtomicReference<ExecutionContext> threadContext = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread otherThread = new Thread(() -> {
            threadContext.set(ExecutionContextHolder.get());
            latch.countDown();
        });
        otherThread.start();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(threadContext.get()).isNull();
        assertThat(ExecutionContextHolder.get()).isSameAs(mainContext);
    }

    @Test
    @DisplayName("Should execute action within context and restore previous context")
    void should_execute_and_restore_context_when_run_with_invoked() {
        ExecutionContext previousContext = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN"));
        ExecutionContext innerContext = new ExecutionContext(UUID.randomUUID(), UUID.randomUUID(), Set.of("USER"));
        ExecutionContextHolder.set(previousContext);

        AtomicReference<ExecutionContext> executedContext = new AtomicReference<>();

        ExecutionContextHolder.runWith(innerContext, () -> executedContext.set(ExecutionContextHolder.get()));

        assertThat(executedContext.get()).isSameAs(innerContext);
        assertThat(ExecutionContextHolder.get()).isSameAs(previousContext);

        ExecutionContextHolder.clear();
        ExecutionContextHolder.runWith(innerContext, () -> executedContext.set(ExecutionContextHolder.get()));
        assertThat(executedContext.get()).isSameAs(innerContext);
        assertThat(ExecutionContextHolder.get()).isNull();
    }
}
