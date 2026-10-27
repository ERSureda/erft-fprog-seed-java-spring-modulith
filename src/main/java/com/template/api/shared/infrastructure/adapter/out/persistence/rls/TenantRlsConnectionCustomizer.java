package com.template.api.shared.infrastructure.adapter.out.persistence.rls;

import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Slf4j
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnBean(JdbcTemplate.class)
public class TenantRlsConnectionCustomizer {

    private static final String TENANT_RLS_APPLIED = "TENANT_RLS_APPLIED";
    private static final String SET_TENANT_SQL = "SELECT set_config('app.current_tenant_id', ?, true)";

    private static final TransactionSynchronization UNBIND_RLS_SYNCHRONIZATION = new TransactionSynchronization() {
        @Override
        public void suspend() {
            TransactionSynchronizationManager.unbindResourceIfPossible(TENANT_RLS_APPLIED);
        }

        @Override
        public void resume() {
            if (!TransactionSynchronizationManager.hasResource(TENANT_RLS_APPLIED)) {
                TransactionSynchronizationManager.bindResource(TENANT_RLS_APPLIED, Boolean.TRUE);
            }
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(TENANT_RLS_APPLIED);
        }
    };

    private final JdbcTemplate jdbcTemplate;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantRls(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = ExecutionContextHolder.getTenantId();
        if (tenantId != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                if (TransactionSynchronizationManager.getResource(TENANT_RLS_APPLIED) == null) {
                    setTenantSessionVariable(tenantId);
                    TransactionSynchronizationManager.bindResource(TENANT_RLS_APPLIED, Boolean.TRUE);
                    TransactionSynchronizationManager.registerSynchronization(UNBIND_RLS_SYNCHRONIZATION);
                }
            } else {
                setTenantSessionVariable(tenantId);
            }
        }
        return joinPoint.proceed();
    }

    public void setTenantSessionVariable(UUID tenantId) {
        if (tenantId != null && jdbcTemplate != null) {
            log.trace("Applying PostgreSQL RLS session variable for tenant: {}", tenantId);
            jdbcTemplate.queryForObject(SET_TENANT_SQL, String.class, tenantId.toString());
        }
    }
}
