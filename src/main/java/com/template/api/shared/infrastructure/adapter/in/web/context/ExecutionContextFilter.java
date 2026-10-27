package com.template.api.shared.infrastructure.adapter.in.web.context;

import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.UuidGeneratorPort;
import com.template.api.shared.infrastructure.adapter.in.web.ApiHeaders;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import com.template.api.shared.infrastructure.adapter.out.uuid.UuidGeneratorAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExecutionContextFilter extends OncePerRequestFilter {

    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_CORRELATION_ID = "correlationId";

    private final UuidGeneratorPort uuidGenerator;

    @Autowired
    public ExecutionContextFilter(@Autowired(required = false) UuidGeneratorPort uuidGenerator) {
        this.uuidGenerator = (uuidGenerator != null) ? uuidGenerator : new UuidGeneratorAdapter();
    }

    public ExecutionContextFilter() {
        this(new UuidGeneratorAdapter());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawTenantId = request.getHeader(ApiHeaders.TENANT_ID);
        String rawUserId = request.getHeader(ApiHeaders.USER_ID);
        String rawCorrelationId = request.getHeader(ApiHeaders.CORRELATION_ID);

        UUID tenantId = parseUuid(rawTenantId);
        UUID userId = parseUuid(rawUserId);

        String correlationId;
        if (rawCorrelationId != null && !rawCorrelationId.isBlank()) {
            correlationId = rawCorrelationId.trim();
        } else {
            correlationId = uuidGenerator.generateIdString();
        }

        Set<String> roles = parseRoles(request.getHeader(ApiHeaders.ROLES));

        ExecutionContext context = new ExecutionContext(
                tenantId,
                userId,
                roles,
                correlationId
        );

        ExecutionContextHolder.set(context);

        if (correlationId != null) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            response.setHeader(ApiHeaders.CORRELATION_ID, correlationId);
        }
        if (tenantId != null) {
            MDC.put(MDC_TENANT_ID, tenantId.toString());
        }
        if (userId != null) {
            MDC.put(MDC_USER_ID, userId.toString());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            ExecutionContextHolder.clear();
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Set<String> parseRoles(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return Set.of();
        }
        if (rawRoles.indexOf(',') < 0) {
            String single = rawRoles.trim();
            return single.isEmpty() ? Set.of() : Set.of(single);
        }
        String[] tokens = rawRoles.split(",");
        Set<String> roles = HashSet.newHashSet(tokens.length);
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                roles.add(trimmed);
            }
        }
        return roles;
    }
}
