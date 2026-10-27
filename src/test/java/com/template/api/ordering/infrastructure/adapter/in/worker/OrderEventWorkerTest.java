package com.template.api.ordering.infrastructure.adapter.in.worker;

import com.template.api.ordering.application.port.in.ProcessOrderPaymentUseCase;
import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@DisplayName("OrderEventWorker Asynchronous & Idempotency Tests")
class OrderEventWorkerTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrderEventWorker orderEventWorker;

    @Autowired(required = false)
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProcessOrderPaymentUseCase processOrderPaymentUseCase;

    @BeforeEach
    void setUp() {
        if (jdbcTemplate != null) {
            jdbcTemplate.getJdbcTemplate().execute("TRUNCATE TABLE shared.processed_events");
        }
    }

    @Test
    @DisplayName("Should deduplicate event when received twice with same event id")
    void should_deduplicate_event_when_received_twice_with_same_event_id() {
        UUID eventId = UUID.randomUUID();
        String payloadJson = """
            {
                "orderId": "%s",
                "paymentStatus": "CONFIRMED"
            }
        """.formatted(UUID.randomUUID());

        orderEventWorker.consumeOrderPaymentEvent(eventId, payloadJson);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared.processed_events WHERE event_id = :id",
                    Map.of("id", eventId),
                    Integer.class
            );
            assertThat(count).isEqualTo(1);
        });

        orderEventWorker.consumeOrderPaymentEvent(eventId, payloadJson);

        verify(processOrderPaymentUseCase, times(1)).execute(any());
    }
}
