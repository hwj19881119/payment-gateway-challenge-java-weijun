package com.checkout.payment.gateway.integration;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.dto.BankResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PaymentDetail;
import com.checkout.payment.gateway.repository.InMemoryPaymentsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayIntegrationTest {

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    BankClient mockBankClient() {
      return Mockito.mock(BankClient.class);
    }
  }

  @Autowired MockMvc mockMvc;
  @Autowired InMemoryPaymentsRepository paymentsRepository;
  @Autowired BankClient bankClient;

  @BeforeEach
  void resetMocks() {
    Mockito.reset(bankClient);
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

  // ===== POST then GET — full happy path =====

  @Test
  void postPaymentThenGetPaymentReturnsSameData() throws Exception {
    when(bankClient.sendPayment(any()))
        .thenReturn(new BankResponse(true, "auth-abc"));

    // Step 1: POST /payments
    MvcResult postResult = mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-post-get")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("1111"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(1000))
        .andReturn();

    // Extract payment id from response
    String responseBody = postResult.getResponse().getContentAsString();
    String paymentId = com.jayway.jsonpath.JsonPath.read(responseBody, "$.id");

    // Step 2: GET /payments/payment/{id}
    mockMvc.perform(get("/payments/{id}", paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.card_number_last_four").value("1111"));
  }

  // ===== POST — bank declines =====

  @Test
  void postPaymentDeclinedByBankReturnsDeclinedStatus() throws Exception {
    when(bankClient.sendPayment(any()))
        .thenReturn(new BankResponse(false, null));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-declined")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  // ===== POST — idempotency: duplicate same request =====

  @Test
  void postPaymentDuplicateRequestReturnsCached() throws Exception {
    when(bankClient.sendPayment(any()))
        .thenReturn(new BankResponse(true, "auth-xyz"));

    // First request — created
    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-duplicate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isCreated());

    // Second request with same key — cached
    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-duplicate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"));
  }

  // ===== POST — idempotency: different body same key =====

  @Test
  void postPaymentDifferentBodySameKeyReturnsConflict() throws Exception {
    when(bankClient.sendPayment(any()))
        .thenReturn(new BankResponse(true, "auth-xyz"));

    // First request
    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-conflict")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isCreated());

    // Second request with different body
    String differentBody = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2030,
          "currency": "USD",
          "amount": 9999,
          "cvv": "123"
        }
        """;

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-conflict")
            .contentType(MediaType.APPLICATION_JSON)
            .content(differentBody))
        .andExpect(status().isConflict());
  }

  // ===== GET — not found =====

  @Test
  void getPaymentRandomIdReturns404() throws Exception {
    mockMvc.perform(get("/payments/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("payment not found"));
  }

  // ===== POST — bank service error =====

  @Test
  void postPaymentBankErrorReturns502() throws Exception {
    when(bankClient.sendPayment(any()))
        .thenThrow(new com.checkout.payment.gateway.exception.BankServiceException("Bank down"));

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-bank-error")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isBadGateway());
  }

  // ===== POST — in-progress: second request while first is still processing =====

  @Test
  void postPaymentInProgressRequestReturns409() throws Exception {
    CountDownLatch bankBlocker = new CountDownLatch(1);

    // First call blocks until we release it, keeping the request IN_PROGRESS
    when(bankClient.sendPayment(any()))
        .thenAnswer(invocation -> {
          bankBlocker.await(5, TimeUnit.SECONDS);
          return new BankResponse(true, "auth-xyz");
        });

    // Send first request in a background thread (it will block at bank call)
    CompletableFuture<Void> firstRequest = CompletableFuture.runAsync(() -> {
      try {
        mockMvc.perform(post("/payments")
                .header("Idempotency-Key", "key-inprogress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Give the first request time to create the IN_PROGRESS idempotency record
    Thread.sleep(500);

    // Second request with same key — should see IN_PROGRESS and return 409
    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-inprogress")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isConflict());

    // Release the blocked first request so it can complete
    bankBlocker.countDown();
    firstRequest.get(5, TimeUnit.SECONDS);
  }

  // ===== POST — validation rejection =====

  @Test
  void postPaymentExpiredCardReturns422() throws Exception {
    String expiredCardBody = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 1,
          "expiry_year": 2020,
          "currency": "USD",
          "amount": 1000,
          "cvv": "123"
        }
        """;

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-expired")
            .contentType(MediaType.APPLICATION_JSON)
            .content(expiredCardBody))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0]").exists());
  }

  @Test
  void postPaymentUnsupportedCurrencyReturns422() throws Exception {
    String unsupportedCurrencyBody = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2030,
          "currency": "JPY",
          "amount": 1000,
          "cvv": "123"
        }
        """;

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-currency")
            .contentType(MediaType.APPLICATION_JSON)
            .content(unsupportedCurrencyBody))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors[0]").exists());
  }

  // ===== POST — missing idempotency key =====

  @Test
  void postPaymentMissingIdempotencyKeyReturns400() throws Exception {
    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").exists());
  }

  // ===== POST — Jakarta validation (wrong arguments) =====

  @Test
  void postPaymentBlankCardNumberReturns400() throws Exception {
    String body = """
        {
          "card_number": "",
          "expiry_month": 12,
          "expiry_year": 2030,
          "currency": "USD",
          "amount": 1000,
          "cvv": "123"
        }
        """;

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-blank-card")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").exists());
  }

  @Test
  void postPaymentNegativeAmountReturns400() throws Exception {
    String body = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 12,
          "expiry_year": 2030,
          "currency": "USD",
          "amount": -100,
          "cvv": "123"
        }
        """;

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-negative-amount")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").exists());
  }

  @Test
  void postPaymentMissingRequiredFieldsReturns400() throws Exception {
    String body = "{}";

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-missing-fields")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").exists());
  }

  @Test
  void postPaymentInvalidCardNumberFormatReturns400() throws Exception {
    String body = """
        {
          "card_number": "abcd1234",
          "expiry_month": 12,
          "expiry_year": 2030,
          "currency": "USD",
          "amount": 1000,
          "cvv": "123"
        }
        """;

    mockMvc.perform(post("/payments")
            .header("Idempotency-Key", "key-non-numeric-card")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.errors").exists());
  }
}
