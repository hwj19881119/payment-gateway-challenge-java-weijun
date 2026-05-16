package com.checkout.payment.gateway.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.checkout.payment.gateway.model.PaymentDetail;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final ConcurrentHashMap<UUID, PaymentDetail> payments = new ConcurrentHashMap<>();

  public PaymentDetail add(PaymentDetail payment) {
    if(payment.getId() == null){
      payment.setId(UUID.randomUUID());
      payment.setCreatedAt(Instant.now());
      payment.setUpdatedAt(Instant.now());
    }else{
      payment.setUpdatedAt(Instant.now());
    }
    payments.put(payment.getId(), payment);
    return payment;
  }

  public Optional<PaymentDetail> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
