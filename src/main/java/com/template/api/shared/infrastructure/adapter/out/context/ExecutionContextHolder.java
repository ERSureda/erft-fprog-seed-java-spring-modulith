package com.template.api.shared.infrastructure.adapter.out.context;

import com.template.api.shared.application.context.ExecutionContext;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class ExecutionContextHolder {

    private static final ThreadLocal<ExecutionContext> CURRENT_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_EVENT_ID = new ThreadLocal<>();

    private ExecutionContextHolder() {}

    public static void set(ExecutionContext context) {
        if (context == null) {
            clear();
        } else {
            CURRENT_CONTEXT.set(context);
        }
    }

    public static ExecutionContext get() {
        return CURRENT_CONTEXT.get();
    }

    public static UUID getTenantId() {
        ExecutionContext ctx = get();
        return ctx != null ? ctx.tenantId() : null;
    }

    public static UUID getUserId() {
        ExecutionContext ctx = get();
        return ctx != null ? ctx.userId() : null;
    }

    public static String getCorrelationId() {
        ExecutionContext ctx = get();
        return ctx != null ? ctx.correlationId() : null;
    }

    public static UUID getEventId() {
        return CURRENT_EVENT_ID.get();
    }

    public static void clear() {
        CURRENT_CONTEXT.remove();
        CURRENT_EVENT_ID.remove();
    }

    public static void runWith(ExecutionContext context, Runnable action) {
        runWith(context, null, action);
    }

    public static void runWith(ExecutionContext context, UUID eventId, Runnable action) {
        Objects.requireNonNull(action, "EXECUTION_CONTEXT_ACTION_CANNOT_BE_NULL");
        ExecutionContext previousContext = CURRENT_CONTEXT.get();
        UUID previousEventId = CURRENT_EVENT_ID.get();
        try {
            set(context);
            if (eventId != null) {
                CURRENT_EVENT_ID.set(eventId);
            } else {
                CURRENT_EVENT_ID.remove();
            }
            action.run();
        } finally {
            restore(previousContext, previousEventId);
        }
    }

    public static <T> T callWith(ExecutionContext context, Supplier<T> action) {
        Objects.requireNonNull(action, "EXECUTION_CONTEXT_ACTION_CANNOT_BE_NULL");
        ExecutionContext previousContext = CURRENT_CONTEXT.get();
        UUID previousEventId = CURRENT_EVENT_ID.get();
        try {
            set(context);
            return action.get();
        } finally {
            restore(previousContext, previousEventId);
        }
    }

    private static void restore(ExecutionContext previousContext, UUID previousEventId) {
        if (previousContext != null) {
            CURRENT_CONTEXT.set(previousContext);
        } else {
            CURRENT_CONTEXT.remove();
        }
        if (previousEventId != null) {
            CURRENT_EVENT_ID.set(previousEventId);
        } else {
            CURRENT_EVENT_ID.remove();
        }
    }
}
