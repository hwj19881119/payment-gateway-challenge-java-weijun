package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PaymentDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import javax.swing.text.html.Option;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
public class PaymentsRepositoryTest {

  private PaymentsRepository repository;

  @BeforeEach
  void setUp(){
    repository = new PaymentsRepository();
  }

  @Test
  void whenPaymentAddedThenItCanBeRetrievedById(){
    PaymentDetail payment = new PaymentDetail();
    payment.setId(UUID.randomUUID());
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setCardNumberLastFour("1234");
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2028);
    payment.setCurrency("GBP");
    payment.setAmount(1000L);
    payment.setAuthorizationCode("auth-code");

    repository.add(payment);

    Optional<PaymentDetail> retrieved = repository.get(payment.getId());

    assertTrue(retrieved.isPresent());
    PaymentDetail result = retrieved.get();
    assertEquals(payment.getId(), result.getId());
    assertEquals(payment.getStatus(), result.getStatus());
    assertEquals(payment.getCardNumberLastFour(), result.getCardNumberLastFour());
    assertEquals(payment.getExpiryMonth(), result.getExpiryMonth());
    assertEquals(payment.getExpiryYear(), result.getExpiryYear());
    assertEquals(payment.getCurrency(), result.getCurrency());
    assertEquals(payment.getAmount(), result.getAmount());
    assertEquals(payment.getAuthorizationCode(), result.getAuthorizationCode());
  }

  @Test
  void whenPaymentNotFoundThenEmptyIsReturned(){
    Optional<PaymentDetail> shouldEmpty = repository.get(UUID.randomUUID());
    assertTrue(shouldEmpty.isEmpty());
  }

  @Test
  void whenPaymentOverwrittenThenLatestIsReturned(){
    UUID id = UUID.randomUUID();
    PaymentDetail payment1 = new PaymentDetail();
    payment1.setId(id);
    payment1.setStatus(PaymentStatus.AUTHORIZED);
    payment1.setCardNumberLastFour("1234");
    payment1.setExpiryMonth(12);
    payment1.setExpiryYear(2028);
    payment1.setCurrency("GBP");
    payment1.setAmount(1000L);
    payment1.setAuthorizationCode("auth-code");

    PaymentDetail payment2 = new PaymentDetail();
    payment2.setId(id);
    payment2.setStatus(PaymentStatus.DECLINED);
    payment2.setCardNumberLastFour("1234");
    payment2.setExpiryMonth(12);
    payment2.setExpiryYear(2028);
    payment2.setCurrency("GBP");
    payment2.setAmount(2000L);
    payment2.setAuthorizationCode("auth-code");

    repository.add(payment1);
    repository.add(payment2);

    Optional<PaymentDetail> result = repository.get(id);
    assertTrue(result.isPresent());
    assertEquals(result.get().getStatus(), PaymentStatus.DECLINED);
    assertEquals(result.get().getAmount(), 2000L);

  }

}
