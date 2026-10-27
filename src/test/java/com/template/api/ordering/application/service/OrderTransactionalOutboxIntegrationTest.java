package com.template.api.ordering.application.service;

import com.template.api.ordering.application.command.CreateOrderCommand;
import com.template.api.shared.application.context.ExecutionContext;
import com.template.api.shared.application.port.out.OutboxPublisherPort;
import com.template.api.shared.domain.exception.InfrastructureException;
import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.template.api.shared.infrastructure.adapter.out.context.ExecutionContextHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@DisplayName("Persistencia: Atomicidad del Transactional Outbox (TRX-03)")
class OrderTransactionalOutboxIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CreateOrderService createOrderService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OutboxPublisherPort outboxPublisherPort;

    @Test
    @DisplayName("Debe revertir tanto el Agregado como el evento Outbox si la transacción falla antes del commit")
    void should_rollback_aggregate_and_outbox_event_when_transaction_fails_before_commit() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ExecutionContextHolder.set(new ExecutionContext(tenantId, customerId, Set.of("USER")));

        try {
            CreateOrderCommand command = new CreateOrderCommand(customerId, new BigDecimal("250.00"), "EUR");

            // Simular fallo técnico en la publicación del Outbox dentro de la transacción activa
            doThrow(new InfrastructureException("Simulated Outbox DB failure", new RuntimeException()))
                    .when(outboxPublisherPort).publish(any(), any(), any(), any());
            doThrow(new InfrastructureException("Simulated Outbox DB failure", new RuntimeException()))
                    .when(outboxPublisherPort).publishAll(any());

            assertThatThrownBy(() -> createOrderService.execute(command))
                    .isInstanceOf(InfrastructureException.class);

            // Desactivar RLS local o enlazar tenant para auditar existencia de filas en caso de fallo de rollback
            jdbcTemplate.getJdbcTemplate().execute("SELECT set_config('app.current_tenant_id', '" + tenantId + "', false)");

            // Verificar que no existen registros huérfanos ni en orders ni en outbox_events
            Integer ordersCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM ordering.orders WHERE customer_id = :customerId",
                    Map.of("customerId", customerId),
                    Integer.class
            );
            Integer outboxCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM shared.outbox_events WHERE payload::text LIKE :match",
                    Map.of("match", "%" + customerId + "%"),
                    Integer.class
            );

            assertThat(ordersCount).isZero();
            assertThat(outboxCount).isZero();
        } finally {
            ExecutionContextHolder.clear();
        }
    }
}
