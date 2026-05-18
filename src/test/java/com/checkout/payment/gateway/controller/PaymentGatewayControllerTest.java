package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.dto.PostPaymentResponseWithStatus;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankServiceException;
import com.checkout.payment.gateway.exception.CommonExceptionHandler;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.service.PaymentGatewayService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayControllerTest {

  @Mock PaymentGatewayService service;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new PaymentGatewayController(service))
        .setControllerAdvice(new CommonExceptionHandler())
        .build();
  }

  private static final String VALID_BODY = """
      {
        "card_number": "4111111111111111",
        "expiry_month": 12,
        "expiry_year": 2030,
        "currency": "USD",
        "amount": 1000,
        "cvv": "123"
      }
      """;

  // ===== GET /payments/payment/{id} =====

  @Test
  void getPaymentByIdFoundReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    PostPaymentResponse response = new PostPaymentResponse(
        id, PaymentStatus.AUTHORIZED, "1111", 12, 2030, "USD", 1000L);
    when(service.getPaymentById(id)).thenReturn(response);

    mockMvc.perform(get("/payments/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(response.id().toString()))
        .andExpect(jsonPath("$.status").value(response.status().getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(response.cardNumberLastFour()))
        .andExpect(jsonPath("$.currency").value(response.currency()))
        .andExpect(jsonPath("$.amount").value(response.amount()));
  }

  @Test
  void getPaymentByIdNotFoundReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.getPaymentById(id))
        .thenThrow(new PaymentNotFoundException("Invalid Payment ID: " + id));

    mockMvc.perform(get("/payments/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("payment not found"));
  }

  // ===== POST /payments — happy path =====

  @Test
  void processPaymentAuthorizedReturns201() throws Exception {
    PostPaymentResponse response = new PostPaymentResponse(
        UUID.randomUUID(), PaymentStatus.AUTHORIZED, "1111", 12, 2030, "USD", 1000L);
    when(service.processPayment(anyString(), any()))
        .thenReturn(PostPaymentResponseWithStatus.newResponse(response));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.card_number_last_four").value(response.cardNumberLastFour()));
  }

  @Test
  void processPaymentCachedReturns200() throws Exception {
    PostPaymentResponse response = new PostPaymentResponse(
        UUID.randomUUID(), PaymentStatus.AUTHORIZED, "1111", 12, 2030, "USD", 1000L);
    when(service.processPayment(anyString(), any()))
        .thenReturn(PostPaymentResponseWithStatus.cachedResponse(response));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(response.status().getName()));
  }

  @Test
  void processPaymentDeclinedReturns201() throws Exception {
    PostPaymentResponse response = new PostPaymentResponse(
        UUID.randomUUID(), PaymentStatus.DECLINED, "1111", 12, 2030, "USD", 1000L);
    when(service.processPayment(anyString(), any()))
        .thenReturn(PostPaymentResponseWithStatus.newResponse(response));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value(PaymentStatus.DECLINED.getName()));
  }

  // ===== POST /payments — exception handling =====

  @Test
  void processPaymentValidationRejectedReturns422() throws Exception {
    when(service.processPayment(anyString(), any()))
        .thenThrow(new PaymentRejectedException(List.of("Card has been expired.")));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0]").value("Card has been expired."));
  }

  @Test
  void processPaymentIdempotencyConflictReturns409() throws Exception {
    when(service.processPayment(anyString(), any()))
        .thenThrow(new IdempotencyConflictException("already in process", "key-123"));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void processPaymentBankErrorReturns502() throws Exception {
    when(service.processPayment(anyString(), any()))
        .thenThrow(new BankServiceException("Bank unavailable"));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void processPaymentMissingIdempotencyKeyReturns400() throws Exception {
    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").exists());
  }

  @Test
  void processPaymentUnexpectedErrorReturns500() throws Exception {
    when(service.processPayment(anyString(), any()))
        .thenThrow(new RuntimeException("something unexpected"));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Internal server error"));
  }
}
