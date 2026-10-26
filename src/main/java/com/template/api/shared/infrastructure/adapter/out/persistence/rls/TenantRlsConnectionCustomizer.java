package com.template.api.shared.infrastructure.adapter.out.persistence.rls;

import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantRlsConnectionCustomizer {

    private final JdbcTemplate jdbcTemplate;

    public TenantRlsConnectionCustomizer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional) || @within(org.springframework.transaction.annotation.Transactional)")
    public Object applyTenantRls(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = ExecutionContextHolder.getTenantId();
        if (tenantId != null) {
            setTenantSessionVariable(tenantId);
        }
        return joinPoint.proceed();
    }

    public void setTenantSessionVariable(UUID tenantId) {
        if (tenantId != null && jdbcTemplate != null) {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', true)");
        }
    }
}
