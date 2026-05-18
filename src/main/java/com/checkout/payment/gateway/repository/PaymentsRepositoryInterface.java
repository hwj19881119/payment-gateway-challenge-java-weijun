package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentDetail;
import java.util.Optional;
import java.util.UUID;

public interface PaymentsRepositoryInterface {

  PaymentDetail add(PaymentDetail payment);

  Optional<PaymentDetail> get(UUID id);
}
