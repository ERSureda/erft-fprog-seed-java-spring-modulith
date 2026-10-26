package com.template.api.shared.infrastructure.adapter.out.context;

import com.template.api.shared.application.context.ExecutionContext;

import java.util.UUID;

public final class ExecutionContextHolder {

    private static final ThreadLocal<ExecutionContext> CURRENT_CONTEXT = new ThreadLocal<>();

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

    public static void clear() {
        CURRENT_CONTEXT.remove();
    }

    public static void runWith(ExecutionContext context, Runnable action) {
        ExecutionContext previous = CURRENT_CONTEXT.get();
        try {
            set(context);
            action.run();
        } finally {
            if (previous != null) {
                CURRENT_CONTEXT.set(previous);
            } else {
                clear();
            }
        }
    }
}
