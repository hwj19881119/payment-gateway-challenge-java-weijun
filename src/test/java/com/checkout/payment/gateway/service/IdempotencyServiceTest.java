package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.checkout.payment.gateway.dto.PostPaymentRequest;
import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.enums.IdempotencyStatus;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.IdempotencyRecord;
import com.checkout.payment.gateway.repository.IdempotencyRecordRepository;
import com.checkout.payment.gateway.service.IdempotencyService.IdempotencyCheckResult;

import com.checkout.payment.gateway.service.IdempotencyService.IdempotencyCheckResult.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
public class IdempotencyServiceTest {


  private IdempotencyService idempotencyService;
  private IdempotencyRecordRepository idempotencyRepository;

  /**
   * generate PostPaymentRequest using different amount
   * @param amount: request amount
   * @return mocked PostPaymentRequest
   */
  private PostPaymentRequest mockRequest(Long amount){
    return new PostPaymentRequest(
        "1234567890123456",
        12,
        2028,
        "USD",
        amount,
        "123"
    );
  }

  /**
   * generate a mocked PostPaymentRequest entity with default amount "1000L"
   * @return a PostPaymentRequest object with default amount "1000L"
   */
  private PostPaymentRequest mockRequest(){
    return mockRequest(1000L);
  }

  /**
   * generate a mocked PostPaymentResponse
   * @return a PostPaymentResponse object
   */
  private PostPaymentResponse mockAuthorizedResponse(){
    return new PostPaymentResponse(
        UUID.randomUUID(),
        PaymentStatus.AUTHORIZED,
        "5678",
        10,
        2028,
        "USD",
        1000L
    );
  }

  @BeforeEach
  void setUp(){
    idempotencyRepository = new IdempotencyRecordRepository();
    idempotencyService = new IdempotencyService(idempotencyRepository);
  }

  private PostPaymentResponse mockDeclinedResponse(){
    return new PostPaymentResponse(
        UUID.randomUUID(),
        PaymentStatus.DECLINED,
        "5678",
        10,
        2028,
        "USD",
        1000L
    );
  }


  @Test
  public void newRequestReturnsCreated(){
    IdempotencyCheckResult result = idempotencyService.checkOrCreateRequest(
        "test-idempotency-key", mockRequest());
    assertEquals(Type.CREATED, result.type());
  }

  @Test
  public void duplicateInProgressRequestReturnsInProgress(){
    String mockKey = "test-idempotency-key";
    PostPaymentRequest mockRequest = mockRequest();
    IdempotencyCheckResult first = idempotencyService.checkOrCreateRequest(mockKey, mockRequest);
    assertEquals(Type.CREATED, first.type());

    Optional<IdempotencyRecord> record = idempotencyRepository.get(mockKey);
    assertTrue(record.isPresent());
    assertEquals(IdempotencyStatus.IN_PROGRESS, record.get().getStatus());

    IdempotencyCheckResult second = idempotencyService.checkOrCreateRequest(mockKey, mockRequest);
    assertEquals(Type.IN_PROGRESS, second.type());
  }

  @Test
  public void completedRequest(){
    String mockKey = "test-idempotency-key";
    PostPaymentRequest mockRequest = mockRequest();
    PostPaymentResponse mockResponse = mockAuthorizedResponse();

    IdempotencyCheckResult first = idempotencyService.checkOrCreateRequest(mockKey, mockRequest);
    Optional<IdempotencyRecord> record = idempotencyRepository.get(mockKey);
    assertTrue(record.isPresent());
    assertEquals(IdempotencyStatus.IN_PROGRESS, record.get().getStatus());

    idempotencyService.completeRequest(mockKey, mockResponse);

    IdempotencyCheckResult second = idempotencyService.checkOrCreateRequest(mockKey, mockRequest);
    record = idempotencyRepository.get(mockKey);
    assertTrue(record.isPresent());
    assertEquals(IdempotencyStatus.COMPLETED, record.get().getStatus());

    assertEquals(Type.CACHED, second.type());
    assertEquals(mockResponse.id(), second.cachedResponse().id());

  }

  @Test
  void differentRequestHashReturnsConflict(){
    String mockKey = "test-idempotency-key";
    // two different mocked requests
    PostPaymentRequest mockRequest1 = mockRequest(2000L);
    PostPaymentRequest mockRequest2 = mockRequest(3000L);

    IdempotencyCheckResult result1 = idempotencyService.checkOrCreateRequest(mockKey, mockRequest1);
    assertEquals(Type.CREATED, result1.type());

    IdempotencyCheckResult result2 = idempotencyService.checkOrCreateRequest(mockKey, mockRequest2);
    assertEquals(Type.CONFLICT, result2.type());
  }

  @Test
  void deleteRequestCheckNonExisting(){
    String mockKey = "test-idempotency-key";
    PostPaymentRequest mockRequest = mockRequest();
    IdempotencyCheckResult first = idempotencyService.checkOrCreateRequest(mockKey, mockRequest);
    assertEquals(Type.CREATED, first.type());

    Optional<IdempotencyRecord> record = idempotencyRepository.get(mockKey);
    assertTrue(record.isPresent());
    assertEquals(IdempotencyStatus.IN_PROGRESS, record.get().getStatus());


    idempotencyService.deleteRequest(mockKey);
    record = idempotencyRepository.get(mockKey);
    assertFalse(record.isPresent());
  }



}
