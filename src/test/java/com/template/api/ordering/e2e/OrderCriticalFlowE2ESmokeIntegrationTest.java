package com.template.api.ordering.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.template.api.ordering.infrastructure.adapter.in.worker.OrderEventWorker;
import com.template.api.ordering.infrastructure.adapter.in.worker.dto.OrderPaymentEventMessage;
import com.template.api.shared.infrastructure.AbstractPostgresIntegrationTest;
import com.template.api.shared.infrastructure.adapter.in.web.ApiHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("E2E / Humo: Flujo Crítico de Conversión de Pedido (Happy Path)")
class OrderCriticalFlowE2ESmokeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderEventWorker orderEventWorker;

    @Autowired(required = false)
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Debe completar el ciclo de vida completo de conversión de pedido: creación, outbox, worker asíncrono, idempotencia y consulta final")
    void should_complete_full_order_conversion_lifecycle_when_happy_path_is_executed() throws Exception {
        // =========================================================================
        // ARRANGE
        // =========================================================================
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        String createOrderPayload = """
            {
                "amount": 150.00,
                "currency": "EUR"
            }
            """;

        // =========================================================================
        // ACT & ASSERT: Paso 3a - Creación de Orden vía HTTP POST
        // =========================================================================
        MvcResult postResult = mockMvc.perform(post("/api/v1/orders")
                        .header(ApiHeaders.TENANT_ID, tenantId.toString())
                        .header(ApiHeaders.USER_ID, userId.toString())
                        .header(ApiHeaders.ROLES, "TENANT_USER")
                        .header(ApiHeaders.CORRELATION_ID, correlationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createOrderPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn();

        JsonNode postResponseJson = objectMapper.readTree(postResult.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(postResponseJson.get("id").asText());

        // =========================================================================
        // ACT & ASSERT: Paso 3b - Persistencia de Outbox
        // =========================================================================
        if (jdbcTemplate != null) {
            Integer outboxCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM shared.outbox_events WHERE aggregate_id = :orderId",
                    Map.of("orderId", orderId.toString()),
                    Integer.class
            );
            assertThat(outboxCount).isNotNull().isGreaterThanOrEqualTo(1);
        }

        // =========================================================================
        // ACT & ASSERT: Paso 3c - Consumo de Evento Asíncrono (Pago Confirmado)
        // =========================================================================
        UUID paymentEventId = UUID.randomUUID();
        String paymentPayloadJson = objectMapper.writeValueAsString(new OrderPaymentEventMessage(
                orderId,
                "CONFIRMED",
                tenantId,
                correlationId
        ));

        orderEventWorker.consumeOrderPaymentEvent(paymentEventId, paymentPayloadJson).join();

        // =========================================================================
        // ACT & ASSERT: Paso 3d - Verificación de Idempotencia en shared.processed_events
        // =========================================================================
        if (jdbcTemplate != null) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Integer processedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM shared.processed_events WHERE event_id = :eventId",
                        Map.of("eventId", paymentEventId),
                        Integer.class
                );
                assertThat(processedCount).isNotNull().isEqualTo(1);
            });
        }

        // =========================================================================
        // ACT & ASSERT: Paso 3e - Consulta de la Orden Confirmada vía HTTP GET
        // =========================================================================
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(ApiHeaders.TENANT_ID, tenantId.toString())
                        .header(ApiHeaders.USER_ID, userId.toString())
                        .header(ApiHeaders.ROLES, "TENANT_USER")
                        .header(ApiHeaders.CORRELATION_ID, correlationId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }
}
