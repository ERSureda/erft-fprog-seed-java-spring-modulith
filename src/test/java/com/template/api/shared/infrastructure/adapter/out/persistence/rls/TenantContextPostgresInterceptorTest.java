package com.template.api.shared.infrastructure.adapter.out.persistence.rls;

import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantContextPostgresInterceptor Unit Tests")
class TenantContextPostgresInterceptorTest {

    private final TenantContextPostgresInterceptor interceptor = new TenantContextPostgresInterceptor();

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("Should inject tenant comment prefix when tenant context is present")
    void should_inject_tenant_comment_when_tenant_context_is_present() {
        UUID tenantId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));

        String sql = "SELECT * FROM orders WHERE id = 1";

        String inspected = interceptor.inspect(sql);

        assertThat(inspected).isEqualTo("/* tenant: " + tenantId + " */ " + sql);
    }

    @Test
    @DisplayName("Should leave SQL unchanged when no tenant is bound to context")
    void should_leave_sql_unchanged_when_no_tenant_in_context() {
        ExecutionContextHolder.set(ExecutionContext.anonymous());
        String sql = "SELECT * FROM orders";

        String inspected = interceptor.inspect(sql);

        assertThat(inspected).isEqualTo(sql);
    }

    @Test
    @DisplayName("Should leave SQL unchanged when it already specifies tenant session variable")
    void should_leave_sql_unchanged_when_already_has_tenant_variable() {
        UUID tenantId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        String sql = "SET LOCAL app.current_tenant_id = '123'; SELECT * FROM orders";

        String inspected = interceptor.inspect(sql);

        assertThat(inspected).isEqualTo(sql);
    }

    @Test
    @DisplayName("Should return null safely when input SQL is null")
    void should_return_null_when_sql_is_null() {
        assertThat(interceptor.inspect(null)).isNull();
    }
}
