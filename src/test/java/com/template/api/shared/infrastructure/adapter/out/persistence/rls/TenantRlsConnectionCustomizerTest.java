package com.template.api.shared.infrastructure.adapter.out.persistence.rls;

import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantRlsConnectionCustomizer Unit Tests")
class TenantRlsConnectionCustomizerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private TenantRlsConnectionCustomizer customizer;

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
    }

    @Test
    @DisplayName("Should execute set_config when tenantId is present in ExecutionContext")
    void should_execute_set_config_when_tenant_id_present() throws Throwable {
        UUID tenantId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        when(joinPoint.proceed()).thenReturn("proceedResult");

        Object result = customizer.applyTenantRls(joinPoint);

        assertThat(result).isEqualTo("proceedResult");
        verify(jdbcTemplate).execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Should not execute set_config when tenantId is absent")
    void should_not_execute_set_config_when_tenant_id_absent() throws Throwable {
        ExecutionContextHolder.clear();
        when(joinPoint.proceed()).thenReturn("proceedResult");

        Object result = customizer.applyTenantRls(joinPoint);

        assertThat(result).isEqualTo("proceedResult");
        verifyNoInteractions(jdbcTemplate);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("setTenantSessionVariable should execute set_config for given tenantId")
    void should_execute_set_config_query_when_tenant_id_provided() {
        UUID tenantId = UUID.randomUUID();

        customizer.setTenantSessionVariable(tenantId);

        verify(jdbcTemplate).execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
    }

    @Test
    @DisplayName("setTenantSessionVariable should do nothing when tenantId is null")
    void should_do_nothing_when_tenant_id_is_null() {
        customizer.setTenantSessionVariable(null);

        verifyNoInteractions(jdbcTemplate);
    }
}
