package com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.adapter;

import com.template.api.ordering.domain.model.Order;
import com.template.api.ordering.domain.model.enums.OrderStatus;
import com.template.api.ordering.fixture.OrderFixture;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.entity.OrderEntity;
import com.template.api.ordering.infrastructure.adapter.out.persistence.postgres.jpa.mapper.OrderPersistenceMapper;
import com.template.api.shared.application.port.out.OutboxPublisherPort;
import com.template.api.shared.domain.event.DomainEvent;
import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private OutboxPublisherPort outboxPublisherPort;

    @Test
    @DisplayName("should persist and hydrate order when saved")
    void should_persist_and_hydrate_order_when_saved() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = OrderFixture.createOrder(orderId, tenantId, customerId, OrderFixture.createMoney());

        // Act
        adapter.save(order);
        Optional<Order> loaded = adapter.findById(order.getId());

        // Assert
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(order.getId());
        assertThat(loaded.get().getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.get().getVersion()).isZero();
        assertThat(loaded.get().getAmount()).isEqualTo(order.getAmount());
        verify(outboxPublisherPort).publish(eq("ordering"), eq("Order"), eq(orderId), any(DomainEvent.class));
    }

    @Test
    @DisplayName("should increment version when order is updated")
    void should_increment_version_when_order_is_updated() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = OrderFixture.createOrder(orderId, tenantId, customerId, OrderFixture.createMoney());

        Order savedOrder = adapter.save(order);
        entityManager.flush();
        entityManager.clear();
        assertThat(savedOrder.getVersion()).isZero();

        // Act
        Order toUpdate = adapter.findById(orderId).orElseThrow();
        toUpdate.confirm();
        adapter.save(toUpdate);
        entityManager.flush();
        entityManager.clear();

        // Assert
        Optional<Order> updated = adapter.findById(orderId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getVersion()).isEqualTo(1L);
        assertThat(updated.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("should throw optimistic locking failure when saving stale version")
    void should_throw_optimistic_locking_failure_when_saving_stale_version() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order initialOrder = OrderFixture.createOrder(orderId, tenantId, customerId, OrderFixture.createMoney());

        adapter.save(initialOrder);
        entityManager.flush();
        entityManager.clear();

        Order currentOrder = adapter.findById(orderId).orElseThrow();
        currentOrder.confirm();
        adapter.save(currentOrder);
        entityManager.flush();
        entityManager.clear();

        Order staleOrder = OrderFixture.reconstructOrder(
                orderId,
                tenantId,
                customerId,
                OrderFixture.createMoney(new BigDecimal("150.00")),
                OrderStatus.PENDING,
                0L,
                initialOrder.getCreatedAt(),
                Instant.now()
        );

        // Act & Assert
        assertThatThrownBy(() -> {
            adapter.save(staleOrder);
            entityManager.flush();
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("should throw data integrity violation when duplicate primary key persisted")
    void should_throw_data_integrity_violation_when_duplicate_primary_key_persisted() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = OrderFixture.createOrder(orderId, tenantId, customerId, OrderFixture.createMoney());

        adapter.save(order);
        entityManager.flush();
        entityManager.clear();

        OrderEntity duplicateEntity = new OrderEntity(
                orderId,
                tenantId,
                customerId,
                new BigDecimal("50.00"),
                "EUR",
                "PENDING",
                0L,
                Instant.now(),
                Instant.now(),
                null
        );

        // Act & Assert
        assertThatThrownBy(() -> {
            entityManager.persist(duplicateEntity);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should throw data integrity violation when negative amount violates check constraint")
    void should_throw_data_integrity_violation_when_negative_amount_violates_check_constraint() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();

        OrderEntity invalidEntity = new OrderEntity(
                orderId,
                tenantId,
                customerId,
                new BigDecimal("-15.00"),
                "EUR",
                "PENDING",
                0L,
                now,
                now,
                null
        );

        // Act & Assert
        assertThatThrownBy(() -> {
            entityManager.persist(invalidEntity);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
