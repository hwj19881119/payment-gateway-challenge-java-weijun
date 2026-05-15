package com.checkout.payment.gateway.repository;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import com.checkout.payment.gateway.model.PaymentDetail;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentsRepository {

  private final HashMap<UUID, PaymentDetail> payments = new HashMap<>();

  public void add(PaymentDetail payment) {
    payments.put(payment.getId(), payment);
  }

  public Optional<PaymentDetail> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }

}
