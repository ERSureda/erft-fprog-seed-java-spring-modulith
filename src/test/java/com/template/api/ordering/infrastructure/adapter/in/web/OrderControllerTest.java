package com.template.api.ordering.infrastructure.adapter.in.web;

import com.template.api.ordering.application.port.in.CreateOrderUseCase;
import com.template.api.ordering.application.port.in.GetOrderByIdUseCase;
import com.template.api.ordering.application.result.OrderResult;
import com.template.api.ordering.domain.OrderingError;
import com.template.api.shared.domain.exception.ResourceNotFoundException;
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

import static org.hamcrest.Matchers.hasItem;
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
    @DisplayName("Debe retornar HTTP 201 Created cuando el payload y las cabeceras perimetrales son válidos")
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
    @DisplayName("Debe retornar HTTP 200 OK con los datos de la orden cuando la orden existe")
    void should_return_200_ok_when_order_exists() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderByIdUseCase.execute(any())).thenReturn(
                new OrderResult(orderId, "CONFIRMED", new BigDecimal("50.00"), "EUR")
        );

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 con token ORDER_AMOUNT_REQUIRED cuando el importe es nulo")
    void should_return_400_bad_request_when_amount_is_missing() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "currency": "EUR"
                            }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("ORDER_AMOUNT_REQUIRED")));
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 con token ORDER_AMOUNT_MIN_VALUE cuando el importe no es positivo")
    void should_return_400_bad_request_when_amount_is_less_than_minimum() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": -5.00,
                                "currency": "EUR"
                            }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("ORDER_AMOUNT_MIN_VALUE")));
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 con token ORDER_CURRENCY_REQUIRED cuando la divisa está vacía")
    void should_return_400_bad_request_when_currency_is_blank() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 100.00,
                                "currency": ""
                            }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("ORDER_CURRENCY_REQUIRED")));
    }

    @Test
    @DisplayName("Debe retornar HTTP 400 con token ORDER_CURRENCY_INVALID_FORMAT cuando el formato de la divisa es inválido")
    void should_return_400_bad_request_when_currency_format_is_invalid() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "amount": 100.00,
                                "currency": "EU1"
                            }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("ORDER_CURRENCY_INVALID_FORMAT")));
    }

    @Test
    @DisplayName("Debe retornar HTTP 404 Not Found con ErrorResponse canónico cuando la orden no existe")
    void should_return_404_not_found_when_order_does_not_exist() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderByIdUseCase.execute(any())).thenThrow(
                new ResourceNotFoundException(OrderingError.ORDERING_ORDER_NOT_FOUND, "Order with id " + orderId + " not found")
        );

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(ApiHeaders.TENANT_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.USER_ID, UUID.randomUUID().toString())
                        .header(ApiHeaders.ROLES, "OPERATOR")
                        .header(ApiHeaders.CORRELATION_ID, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDERING_ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Order with id " + orderId + " not found"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
