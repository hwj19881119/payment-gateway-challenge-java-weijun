package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.IdempotencyRecord;
import java.util.Optional;

public interface IdempotencyRepositoryInterface {

  IdempotencyRecord save(IdempotencyRecord record);

  Optional<IdempotencyRecord> get(String idempotencyKey);

  void delete(String idempotencyKey);
}
