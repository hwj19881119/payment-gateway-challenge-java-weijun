package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.dto.BankRequest;
import com.checkout.payment.gateway.dto.BankResponse;
import com.checkout.payment.gateway.dto.PostPaymentRequest;
import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.dto.ProcessPaymentResult;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankServiceException;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PaymentDetail;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.service.IdempotencyService.IdempotencyCheckResult;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  PaymentsRepository paymentsRepository;
  @Mock IdempotencyService idempotencyService;
  @Mock PaymentRequestValidator validator;
  @Mock BankClient bankClient;

  @InjectMocks PaymentGatewayService service;

  private static final String IDEMPOTENCY_KEY = "test-key-123";

  private PostPaymentRequest validRequest() {
    return new PostPaymentRequest(
        "4111111111111111", 12, 2030, "USD", 1000L, "123");
  }

  private PaymentDetail buildSavedPayment(PostPaymentRequest request, boolean authorized) {
    PaymentDetail p = new PaymentDetail();
    p.setId(UUID.randomUUID());
    p.setStatus(authorized ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    p.setCardNumberLastFour(request.getCardNumberLastFour());
    p.setExpiryMonth(request.expiryMonth());
    p.setExpiryYear(request.expiryYear());
    p.setCurrency(request.currency());
    p.setAmount(request.amount());
    p.setAuthorizationCode(authorized ? "auth-code" : null);
    return p;
  }

  // ===== getPaymentById =====

  @Test
  void getPaymentByIdFoundReturnsResponse() {
    UUID id = UUID.randomUUID();
    PaymentDetail detail = buildSavedPayment(validRequest(), true);
    when(paymentsRepository.get(id)).thenReturn(Optional.of(detail));

    PostPaymentResponse response = service.getPaymentById(id);

    assertEquals(detail.getId(), response.id());
    assertEquals(detail.getStatus(), response.status());
    assertEquals(detail.getCardNumberLastFour(), response.cardNumberLastFour());
    assertEquals(detail.getCurrency(), response.currency());
    assertEquals(detail.getAmount(), response.amount());
  }

  @Test
  void getPaymentByIdNotFoundThrowsException() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThrows(PaymentNotFoundException.class, () -> service.getPaymentById(id));
  }

  // ===== processPayment — idempotency branches =====

  @Test
  void processPaymentInProgressThrowsConflict() {
    when(idempotencyService.checkOrCreateRequest(anyString(), any()))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.IN_PROGRESS, null));

    assertThrows(IdempotencyConflictException.class,
        () -> service.processPayment(IDEMPOTENCY_KEY, validRequest()));

    verifyNoInteractions(validator, bankClient, paymentsRepository);
  }

  @Test
  void processPaymentConflictThrowsConflict() {
    when(idempotencyService.checkOrCreateRequest(anyString(), any()))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CONFLICT, null));

    assertThrows(IdempotencyConflictException.class,
        () -> service.processPayment(IDEMPOTENCY_KEY, validRequest()));

    verifyNoInteractions(validator, bankClient, paymentsRepository);
  }

  @Test
  void processPaymentCachedReturnsCachedResponseWithOkStatus() {
    PostPaymentResponse cached = new PostPaymentResponse(
        UUID.randomUUID(), PaymentStatus.AUTHORIZED, "1111", 12, 2030, "USD", 1000L);

    when(idempotencyService.checkOrCreateRequest(anyString(), any()))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CACHED, cached));

    ProcessPaymentResult result =
        service.processPayment(IDEMPOTENCY_KEY, validRequest());

    assertEquals(cached, result.response());
    assertEquals(true, result.cached());
    verifyNoInteractions(validator, bankClient, paymentsRepository);
  }

  // ===== processPayment rejected — validation failure =====

  @Test
  void processPaymentValidationFailsThrowsPaymentRejected() {
    when(idempotencyService.checkOrCreateRequest(anyString(), any()))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CREATED, null));
    when(validator.validate(any())).thenReturn(List.of("Card has been expired."));

    PaymentRejectedException ex = assertThrows(PaymentRejectedException.class,
        () -> service.processPayment(IDEMPOTENCY_KEY, validRequest()));

    assertEquals(List.of("Card has been expired."), ex.getErrors());
    verify(idempotencyService).deleteRequest(IDEMPOTENCY_KEY);
    verifyNoInteractions(bankClient, paymentsRepository);
  }

  // ===== processPayment created — happy path (authorized) =====

  @Test
  void processPaymentAuthorizedReturnsCreatedResponse() {
    PostPaymentRequest request = validRequest();

    when(idempotencyService.checkOrCreateRequest(IDEMPOTENCY_KEY, request))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CREATED, null));
    when(validator.validate(request)).thenReturn(Collections.emptyList());
    when(bankClient.sendPayment(any(BankRequest.class)))
        .thenReturn(new BankResponse(true, "auth-123"));
    when(paymentsRepository.add(any(PaymentDetail.class)))
        .thenAnswer(invocation -> {
          PaymentDetail pd = invocation.getArgument(0);
          pd.setId(UUID.randomUUID());
          return pd;
        });

    ProcessPaymentResult result =
        service.processPayment(IDEMPOTENCY_KEY, request);

    assertEquals(false, result.cached());
    assertEquals(PaymentStatus.AUTHORIZED, result.response().status());
    assertEquals(request.getCardNumberLastFour(), result.response().cardNumberLastFour());
    assertEquals(request.currency(), result.response().currency());
    assertEquals(request.amount(), result.response().amount());

    // verify bank request mapping
    ArgumentCaptor<BankRequest> bankCaptor = ArgumentCaptor.forClass(BankRequest.class);
    verify(bankClient).sendPayment(bankCaptor.capture());
    assertEquals(request.cardNumber(), bankCaptor.getValue().cardNumber());
    assertEquals(request.getExpiryDate(), bankCaptor.getValue().expiryDate());
    assertEquals(request.currency(), bankCaptor.getValue().currency());
    assertEquals(request.amount(), bankCaptor.getValue().amount());
    assertEquals(request.cvv(), bankCaptor.getValue().cvv());

    // verify idempotency completion
    verify(idempotencyService).completeRequest(eq(IDEMPOTENCY_KEY), any(PostPaymentResponse.class));
  }

  // ===== processPayment declined — declined by bank =====

  @Test
  void processPaymentDeclinedReturnsDeclinedStatus() {
    PostPaymentRequest request = validRequest();

    when(idempotencyService.checkOrCreateRequest(IDEMPOTENCY_KEY, request))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CREATED, null));
    when(validator.validate(request)).thenReturn(Collections.emptyList());
    when(bankClient.sendPayment(any(BankRequest.class)))
        .thenReturn(new BankResponse(false, null));
    when(paymentsRepository.add(any(PaymentDetail.class)))
        .thenAnswer(invocation -> {
          PaymentDetail pd = invocation.getArgument(0);
          pd.setId(UUID.randomUUID());
          return pd;
        });

    ProcessPaymentResult result =
        service.processPayment(IDEMPOTENCY_KEY, request);

    assertEquals(false, result.cached());
    assertEquals(PaymentStatus.DECLINED, result.response().status());
  }

  // ===== processPayment bad gateway — bank service exception =====

  @Test
  void processPaymentBankExceptionThrowsAndDoesNotSave() {
    PostPaymentRequest request = validRequest();

    when(idempotencyService.checkOrCreateRequest(IDEMPOTENCY_KEY, request))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CREATED, null));
    when(validator.validate(request)).thenReturn(Collections.emptyList());
    when(bankClient.sendPayment(any(BankRequest.class)))
        .thenThrow(new BankServiceException("Bank unavailable"));

    assertThrows(BankServiceException.class,
        () -> service.processPayment(IDEMPOTENCY_KEY, request));

    verify(idempotencyService).deleteRequest(IDEMPOTENCY_KEY);
    verifyNoInteractions(paymentsRepository);
  }

  // ===== processPayment — payment detail mapping =====

  @Test
  void processPaymentMapsRequestFieldsToPaymentDetail() {
    PostPaymentRequest request = validRequest();

    when(idempotencyService.checkOrCreateRequest(IDEMPOTENCY_KEY, request))
        .thenReturn(new IdempotencyCheckResult(IdempotencyCheckResult.Type.CREATED, null));
    when(validator.validate(request)).thenReturn(Collections.emptyList());
    when(bankClient.sendPayment(any(BankRequest.class)))
        .thenReturn(new BankResponse(true, "auth-xyz"));
    when(paymentsRepository.add(any(PaymentDetail.class)))
        .thenAnswer(invocation -> {
          PaymentDetail pd = invocation.getArgument(0);
          pd.setId(UUID.randomUUID());
          return pd;
        });

    service.processPayment(IDEMPOTENCY_KEY, request);

    ArgumentCaptor<PaymentDetail> captor = ArgumentCaptor.forClass(PaymentDetail.class);
    verify(paymentsRepository).add(captor.capture());

    PaymentDetail stored = captor.getValue();
    assertEquals(PaymentStatus.AUTHORIZED, stored.getStatus());
    assertEquals(request.getCardNumberLastFour(), stored.getCardNumberLastFour());
    assertEquals(request.expiryMonth(), stored.getExpiryMonth());
    assertEquals(request.expiryYear(), stored.getExpiryYear());
    assertEquals(request.currency(), stored.getCurrency());
    assertEquals(request.amount(), stored.getAmount());
    assertEquals("auth-xyz", stored.getAuthorizationCode());
  }
}
