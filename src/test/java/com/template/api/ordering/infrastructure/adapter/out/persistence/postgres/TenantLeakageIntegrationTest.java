package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres;

import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Seguridad: Test de Fuga entre Inquilinos (RLS Mandatorio)")
class TenantLeakageIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Los registros del Tenant A deben ser completamente invisibles para el Tenant B")
    void should_prevent_tenant_b_access_when_reading_or_mutating_tenant_a_records() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID orderIdA = UUID.randomUUID();

        // 1. Activar sesión como Tenant A e insertar un registro
        jdbcTemplate.getJdbcTemplate().execute("SELECT set_config('app.current_tenant_id', '" + tenantA + "', false)");
        jdbcTemplate.update("""
            INSERT INTO ordering.orders (id, tenant_id, customer_id, amount, currency, status, version)
            VALUES (:id, :tenantId, :customerId, 100.00, 'EUR', 'PENDING', 0)
            """, Map.of("id", orderIdA, "tenantId", tenantA, "customerId", UUID.randomUUID()));

        // Confirmar que Tenant A sí ve su propio registro
        Integer countA = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ordering.orders WHERE id = :id",
            Map.of("id", orderIdA),
            Integer.class
        );
        assertThat(countA).isEqualTo(1);

        // 2. Cambiar contexto de sesión hacia Tenant B
        jdbcTemplate.getJdbcTemplate().execute("SELECT set_config('app.current_tenant_id', '" + tenantB + "', false)");

        // 3. Verificar que Tenant B recibe CERO resultados (RLS en acción)
        Integer countB = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ordering.orders WHERE id = :id",
            Map.of("id", orderIdA),
            Integer.class
        );
        assertThat(countB).isZero();

        // 4. Verificar que Tenant B no puede mutar datos del Tenant A
        int rowsUpdated = jdbcTemplate.update(
            "UPDATE ordering.orders SET status = 'CANCELLED' WHERE id = :id",
            Map.of("id", orderIdA)
        );
        assertThat(rowsUpdated).isZero();
    }
}
