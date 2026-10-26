package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.adapter;

import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.fixture.OrderFixture;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.mapper.OrderPersistenceMapper;
import com.template.api.shared.application.port.out.OutboxPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderPersistenceAdapter.class, OrderPersistenceMapper.class})
@DisplayName("OrderPersistenceAdapter Integration Tests")
class OrderPersistenceAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderPersistenceAdapter adapter;

    @MockitoBean
    private OutboxPublisherPort outboxPublisherPort;

    @Test
    @DisplayName("should_persist_and_hydrate_order_when_saved")
    void should_persist_and_hydrate_order_when_saved() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = OrderFixture.createOrder(orderId, tenantId, customerId, OrderFixture.createMoney());

        adapter.save(order);
        Optional<Order> loaded = adapter.findById(order.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(order.getId());
        assertThat(loaded.get().getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.get().getVersion()).isZero();
        assertThat(loaded.get().getAmount()).isEqualTo(order.getAmount());
        verify(outboxPublisherPort).publish(eq("ordering"), eq("Order"), eq(orderId), any(DomainEvent.class));
    }
}
