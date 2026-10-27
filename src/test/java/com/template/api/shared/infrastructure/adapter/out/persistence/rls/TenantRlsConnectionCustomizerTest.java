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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantRlsConnectionCustomizer Unit Tests")
class TenantRlsConnectionCustomizerTest {

    private static final String SET_TENANT_SQL = "SELECT set_config('app.current_tenant_id', ?, true)";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private TenantRlsConnectionCustomizer customizer;

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
        TransactionSynchronizationManager.unbindResourceIfPossible("TENANT_RLS_APPLIED");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Should execute set_config when tenantId is present in ExecutionContext")
    void should_execute_set_config_when_tenant_id_present() throws Throwable {
        UUID tenantId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        when(joinPoint.proceed()).thenReturn("proceedResult");

        Object result = customizer.applyTenantRls(joinPoint);

        assertThat(result).isEqualTo("proceedResult");
        verify(jdbcTemplate).queryForObject(SET_TENANT_SQL, String.class, tenantId.toString());
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
    @DisplayName("Should execute set_config only once for nested transactional calls within same transaction")
    void should_execute_set_config_only_once_for_nested_calls_within_same_transaction() throws Throwable {
        TransactionSynchronizationManager.initSynchronization();

        UUID tenantId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        when(joinPoint.proceed()).thenReturn("result1", "result2");

        // First call in transaction: sets config and binds synchronization
        Object result1 = customizer.applyTenantRls(joinPoint);
        assertThat(result1).isEqualTo("result1");

        // Nested call in same transaction: must skip DB execution
        Object result2 = customizer.applyTenantRls(joinPoint);
        assertThat(result2).isEqualTo("result2");

        verify(jdbcTemplate, times(1)).queryForObject(SET_TENANT_SQL, String.class, tenantId.toString());
    }

    @Test
    @DisplayName("Should unbind synchronization resource after transaction completion")
    void should_unbind_synchronization_resource_after_transaction_completion() throws Throwable {
        TransactionSynchronizationManager.initSynchronization();

        UUID tenantId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("USER")));
        when(joinPoint.proceed()).thenReturn("first");

        customizer.applyTenantRls(joinPoint);
        assertThat(TransactionSynchronizationManager.getResource("TENANT_RLS_APPLIED")).isEqualTo(Boolean.TRUE);

        // Simulate Spring's transaction completion callback
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization sync : synchronizations) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }

        assertThat(TransactionSynchronizationManager.getResource("TENANT_RLS_APPLIED")).isNull();
    }

    @Test
    @DisplayName("setTenantSessionVariable should execute set_config for given tenantId")
    void should_execute_set_config_query_when_tenant_id_provided() {
        UUID tenantId = UUID.randomUUID();

        customizer.setTenantSessionVariable(tenantId);

        verify(jdbcTemplate).queryForObject(SET_TENANT_SQL, String.class, tenantId.toString());
    }

    @Test
    @DisplayName("setTenantSessionVariable should do nothing when tenantId is null")
    void should_do_nothing_when_tenant_id_is_null() {
        customizer.setTenantSessionVariable(null);

        verifyNoInteractions(jdbcTemplate);
    }
}
