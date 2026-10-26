package com.template.api.ordering.infrastructure.adapter.in.web;

import com.template.api.ordering.application.port.in.CreateOrderUseCase;
import com.template.api.ordering.application.port.in.GetOrderByIdUseCase;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.shared.infrastructure.adapter.in.web.ApiHeaders;
import com.template.api.shared.infrastructure.adapter.in.web.context.ExecutionContextFilter;
import com.template.api.shared.infrastructure.adapter.in.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({GlobalExceptionHandler.class, ExecutionContextFilter.class})
@DisplayName("OrderController Web Slice Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private GetOrderByIdUseCase getOrderByIdUseCase;

    @Test
    @DisplayName("should_return_201_created_when_payload_is_valid")
    void should_return_201_created_when_payload_is_valid() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(createOrderUseCase.execute(any())).thenReturn(
                new OrderResult(orderId, "PENDING", new BigDecimal("100.00"), "EUR")
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 100.00,
                                "currency": "EUR"
                            }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    @DisplayName("should_return_400_bad_request_when_validation_fails")
    void should_return_400_bad_request_when_validation_fails() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": -5.00,
                                "currency": ""
                            }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    @DisplayName("should_return_200_ok_when_order_exists")
    void should_return_200_ok_when_order_exists() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderByIdUseCase.execute(any())).thenReturn(
                new OrderResult(orderId, "CONFIRMED", new BigDecimal("50.00"), "EUR")
        );

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }
}
