package com.template.api.shared.application.context;

import com.template.api.shared.domain.exception.ForbiddenException;
import com.template.api.shared.domain.exception.UnauthenticatedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionContext Unit Tests")
class ExecutionContextTest {

    @Test
    @DisplayName("Should create context with full attributes")
    void should_store_values_when_full_attributes_provided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Set<String> roles = Set.of("ROLE_USER", "ROLE_ADMIN");

        ExecutionContext context = new ExecutionContext(tenantId, userId, roles);

        assertThat(context.tenantId()).isEqualTo(tenantId);
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.roles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(context.isAuthenticated()).isTrue();
        assertThat(context.hasTenant()).isTrue();
        assertThat(context.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(context.hasRole("ROLE_GUEST")).isFalse();
        assertThat(context.hasRole(null)).isFalse();
    }

    @Test
    @DisplayName("Should resolve UserType.SAAS_ADMIN when role contains SAAS_ADMIN or ROLE_SAAS_ADMIN")
    void should_resolve_saas_admin_when_role_contains_saas_admin() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ExecutionContext ctx1 = new ExecutionContext(tenantId, userId, Set.of("SAAS_ADMIN"));
        ExecutionContext ctx2 = new ExecutionContext(null, userId, Set.of("ROLE_SAAS_ADMIN"));

        assertThat(ctx1.userType()).isEqualTo(UserType.SAAS_ADMIN);
        assertThat(ctx1.isSaasAdmin()).isTrue();
        assertThat(ctx1.isTenantUser()).isFalse();
        assertThat(ctx1.isClient()).isFalse();

        assertThat(ctx2.userType()).isEqualTo(UserType.SAAS_ADMIN);
        assertThat(ctx2.isSaasAdmin()).isTrue();
    }

    @Test
    @DisplayName("Should resolve UserType.TENANT_USER when user has tenantId and is not SAAS_ADMIN")
    void should_resolve_tenant_user_when_user_has_tenant_and_not_saas_admin() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ExecutionContext ctx = new ExecutionContext(tenantId, userId, Set.of("USER"));

        assertThat(ctx.userType()).isEqualTo(UserType.TENANT_USER);
        assertThat(ctx.isTenantUser()).isTrue();
        assertThat(ctx.isSaasAdmin()).isFalse();
        assertThat(ctx.isClient()).isFalse();
    }

    @Test
    @DisplayName("Should resolve UserType.CLIENT when user has no tenantId and is not SAAS_ADMIN")
    void should_resolve_client_when_user_has_no_tenant_and_not_saas_admin() {
        UUID userId = UUID.randomUUID();

        ExecutionContext ctx = new ExecutionContext(null, userId, Set.of("USER"));

        assertThat(ctx.userType()).isEqualTo(UserType.CLIENT);
        assertThat(ctx.isClient()).isTrue();
        assertThat(ctx.isTenantUser()).isFalse();
        assertThat(ctx.isSaasAdmin()).isFalse();
    }

    @Test
    @DisplayName("Should resolve UserType.ANONYMOUS when userId is null")
    void should_resolve_anonymous_when_user_id_is_null() {
        ExecutionContext ctx = new ExecutionContext(null, null, Set.of("GUEST"));

        assertThat(ctx.userType()).isEqualTo(UserType.ANONYMOUS);
        assertThat(ctx.isAuthenticated()).isFalse();
    }

    @Test
    @DisplayName("Should provide cached anonymous singleton")
    void should_provide_cached_anonymous_singleton_when_invoked() {
        ExecutionContext anon = ExecutionContext.anonymous();

        assertThat(anon.userType()).isEqualTo(UserType.ANONYMOUS);
        assertThat(anon.userId()).isNull();
        assertThat(anon.tenantId()).isNull();
        assertThat(anon.roles()).isEmpty();
        assertThat(anon.correlationId()).isNull();
        assertThat(anon.isAuthenticated()).isFalse();
        assertThat(anon.hasTenant()).isFalse();
        assertThat(anon.hasCorrelationId()).isFalse();
    }

    @Test
    @DisplayName("Should support 4-argument constructor with correlationId")
    void should_store_trimmed_value_when_constructor_with_correlation_id_invoked() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ExecutionContext ctx = new ExecutionContext(tenantId, userId, Set.of("USER"), "  corr-123  ");

        assertThat(ctx.correlationId()).isEqualTo("corr-123");
        assertThat(ctx.hasCorrelationId()).isTrue();
    }

    @Test
    @DisplayName("Blank correlationId should be normalized to null")
    void should_normalize_to_null_when_correlation_id_is_blank() {
        ExecutionContext ctx = new ExecutionContext(null, null, null, "   ");

        assertThat(ctx.correlationId()).isNull();
        assertThat(ctx.hasCorrelationId()).isFalse();
    }

    @Test
    @DisplayName("hasAnyRole should check against varargs without collection allocations")
    void should_evaluate_roles_correctly_when_has_any_role_invoked() {
        ExecutionContext ctx = new ExecutionContext(null, UUID.randomUUID(), Set.of("OPERATOR", "VIEWER"));

        assertThat(ctx.hasAnyRole("ADMIN", "OPERATOR")).isTrue();
        assertThat(ctx.hasAnyRole("ADMIN", "SUPERUSER")).isFalse();
        assertThat(ctx.hasAnyRole()).isFalse();
        assertThat(ctx.hasAnyRole((String[]) null)).isFalse();
        assertThat(ctx.hasAnyRole((String) null)).isFalse();

        ExecutionContext emptyRolesCtx = new ExecutionContext(null, UUID.randomUUID(), Set.of());
        assertThat(emptyRolesCtx.hasAnyRole("ADMIN")).isFalse();
    }

    @Test
    @DisplayName("requireUserId should return userId or throw UnauthenticatedException")
    void should_return_user_id_or_throw_when_require_user_id_invoked() {
        UUID userId = UUID.randomUUID();
        ExecutionContext authed = new ExecutionContext(null, userId, Set.of());
        ExecutionContext anon = ExecutionContext.anonymous();

        assertThat(authed.requireUserId()).isEqualTo(userId);
        assertThatThrownBy(anon::requireUserId)
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("requireTenantId should return tenantId or throw ForbiddenException")
    void should_return_tenant_id_or_throw_when_require_tenant_id_invoked() {
        UUID tenantId = UUID.randomUUID();
        ExecutionContext withTenant = new ExecutionContext(tenantId, UUID.randomUUID(), Set.of());
        ExecutionContext withoutTenant = new ExecutionContext(null, UUID.randomUUID(), Set.of());

        assertThat(withTenant.requireTenantId()).isEqualTo(tenantId);
        assertThatThrownBy(withoutTenant::requireTenantId)
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    @DisplayName("hasTenantRole should verify both tenant existence and role presence")
    void should_verify_tenant_and_role_when_has_tenant_role_invoked() {
        UUID tenantId = UUID.randomUUID();
        ExecutionContext ctx = new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("TENANT_ADMIN"));
        ExecutionContext noTenantCtx = new ExecutionContext(null, UUID.randomUUID(), Set.of("TENANT_ADMIN"));

        assertThat(ctx.hasTenantRole("TENANT_ADMIN")).isTrue();
        assertThat(ctx.hasTenantRole("TENANT_USER")).isFalse();
        assertThat(noTenantCtx.hasTenantRole("TENANT_ADMIN")).isFalse();
    }

    @Test
    @DisplayName("requireTenantRole should succeed or throw ForbiddenException")
    void should_succeed_or_throw_forbidden_when_require_tenant_role_invoked() {
        UUID tenantId = UUID.randomUUID();
        ExecutionContext validCtx = new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("TENANT_ADMIN"));
        ExecutionContext missingRoleCtx = new ExecutionContext(tenantId, UUID.randomUUID(), Set.of("TENANT_USER"));
        ExecutionContext missingTenantCtx = new ExecutionContext(null, UUID.randomUUID(), Set.of("TENANT_ADMIN"));

        validCtx.requireTenantRole("TENANT_ADMIN");

        assertThatThrownBy(() -> missingRoleCtx.requireTenantRole("TENANT_ADMIN"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Missing required tenant role: TENANT_ADMIN");

        assertThatThrownBy(() -> missingTenantCtx.requireTenantRole("TENANT_ADMIN"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    @DisplayName("Roles collection should be unmodifiable and defensively copied")
    void should_defend_against_mutation_when_roles_collection_accessed() {
        Set<String> mutableRoles = new HashSet<>();
        mutableRoles.add("ROLE_A");

        ExecutionContext ctx = new ExecutionContext(null, UUID.randomUUID(), mutableRoles);
        mutableRoles.add("ROLE_B");

        assertThat(ctx.roles()).containsExactly("ROLE_A");
        assertThatThrownBy(() -> ctx.roles().add("ROLE_C"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
