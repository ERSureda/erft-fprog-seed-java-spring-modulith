package com.template.api.shared.infrastructure.adapter.in.web.context;

import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.infrastructure.adapter.in.web.ApiHeaders;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionContextFilter Unit Tests")
class ExecutionContextFilterTest {

    private ExecutionContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ExecutionContextFilter();
        ExecutionContextHolder.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        ExecutionContextHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Should extract headers and populate ExecutionContext and MDC for downstream filter")
    void should_populate_context_and_mdc_when_all_headers_provided() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = "corr-123";

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiHeaders.TENANT_ID, tenantId.toString());
        request.addHeader(ApiHeaders.USER_ID, userId.toString());
        request.addHeader(ApiHeaders.ROLES, "ADMIN, OPERATOR");
        request.addHeader(ApiHeaders.CORRELATION_ID, correlationId);

        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ExecutionContext> contextInChain = new AtomicReference<>();
        AtomicReference<String> mdcTenantInChain = new AtomicReference<>();
        AtomicReference<String> mdcUserInChain = new AtomicReference<>();
        AtomicReference<String> mdcCorrelationInChain = new AtomicReference<>();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                contextInChain.set(ExecutionContextHolder.get());
                mdcTenantInChain.set(MDC.get("tenantId"));
                mdcUserInChain.set(MDC.get("userId"));
                mdcCorrelationInChain.set(MDC.get("correlationId"));
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(contextInChain.get()).isNotNull();
        assertThat(contextInChain.get().tenantId()).isEqualTo(tenantId);
        assertThat(contextInChain.get().userId()).isEqualTo(userId);
        assertThat(contextInChain.get().roles()).containsExactlyInAnyOrder("ADMIN", "OPERATOR");
        assertThat(contextInChain.get().correlationId()).isEqualTo(correlationId);

        assertThat(mdcTenantInChain.get()).isEqualTo(tenantId.toString());
        assertThat(mdcUserInChain.get()).isEqualTo(userId.toString());
        assertThat(mdcCorrelationInChain.get()).isEqualTo(correlationId);

        assertThat(response.getHeader(ApiHeaders.CORRELATION_ID)).isEqualTo(correlationId);

        assertThat(ExecutionContextHolder.get()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("Should default to anonymous context when headers are missing")
    void should_default_to_anonymous_when_headers_missing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ExecutionContext> contextInChain = new AtomicReference<>();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                contextInChain.set(ExecutionContextHolder.get());
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(contextInChain.get()).isNotNull();
        assertThat(contextInChain.get().tenantId()).isNull();
        assertThat(contextInChain.get().userId()).isNull();
        assertThat(contextInChain.get().roles()).isEmpty();

        assertThat(ExecutionContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Should safely ignore malformed UUIDs without throwing")
    void should_ignore_gracefully_when_uuids_are_invalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiHeaders.TENANT_ID, "not-a-uuid");
        request.addHeader(ApiHeaders.USER_ID, "invalid-user-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ExecutionContext> contextInChain = new AtomicReference<>();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                contextInChain.set(ExecutionContextHolder.get());
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(contextInChain.get()).isNotNull();
        assertThat(contextInChain.get().tenantId()).isNull();
        assertThat(contextInChain.get().userId()).isNull();
    }

    @Test
    @DisplayName("Should parse single role without commas")
    void should_parse_correctly_when_single_role_provided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiHeaders.ROLES, "ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ExecutionContext> contextInChain = new AtomicReference<>();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                contextInChain.set(ExecutionContextHolder.get());
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(contextInChain.get().roles()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Should parse multiple roles with spaces and empty tokens safely")
    void should_parse_multiple_roles_when_spaces_and_empty_tokens_present() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiHeaders.ROLES, "ROLE_ADMIN, , ROLE_USER , , ROLE_MANAGER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ExecutionContext> contextInChain = new AtomicReference<>();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                contextInChain.set(ExecutionContextHolder.get());
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(contextInChain.get().roles()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("Should always clear context and MDC even when filter chain throws an exception")
    void should_always_clear_context_when_filter_chain_throws_exception() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiHeaders.TENANT_ID, UUID.randomUUID().toString());
        request.addHeader(ApiHeaders.USER_ID, UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain failingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new RuntimeException("Simulated chain failure");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated chain failure");

        assertThat(ExecutionContextHolder.get()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("Should propagate custom correlationId and set it on response header")
    void should_propagate_correlation_id_when_custom_header_provided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiHeaders.CORRELATION_ID, "req-xyz-987");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ExecutionContext> contextInChain = new AtomicReference<>();
        AtomicReference<String> mdcCorrelationInChain = new AtomicReference<>();

        MockFilterChain filterChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                contextInChain.set(ExecutionContextHolder.get());
                mdcCorrelationInChain.set(MDC.get("correlationId"));
            }
        };

        filter.doFilter(request, response, filterChain);

        assertThat(contextInChain.get().correlationId()).isEqualTo("req-xyz-987");
        assertThat(mdcCorrelationInChain.get()).isEqualTo("req-xyz-987");
        assertThat(response.getHeader(ApiHeaders.CORRELATION_ID)).isEqualTo("req-xyz-987");
        assertThat(MDC.get("correlationId")).isNull();
    }
}
