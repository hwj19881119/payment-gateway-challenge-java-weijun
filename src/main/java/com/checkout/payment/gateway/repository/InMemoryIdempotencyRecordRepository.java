package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.IdempotencyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryIdempotencyRecordRepository implements IdempotencyRepository {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryIdempotencyRecordRepository.class);

  private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

  @Override
  public IdempotencyRecord save(IdempotencyRecord record){
    if(record.getId() == null){
      record.setId(UUID.randomUUID());
      record.setCreatedAt(Instant.now());
    }
    record.setUpdatedAt(Instant.now());
    records.put(record.getIdempotencyKey(), record);
    LOG.debug("Idempotency key save: {}, status: {}", record.getIdempotencyKey(), record.getStatus());
    return record;
  }

  @Override
  public Optional<IdempotencyRecord> get(String idempotencyKey){
    return Optional.ofNullable(records.get(idempotencyKey));
  }

  @Override
  public void delete(String idempotencyKey){
    IdempotencyRecord removed = records.remove(idempotencyKey);
    if(removed != null){
      LOG.debug("Idempotency key deleted: key={}", idempotencyKey);
    }else{
      LOG.warn("Idempotency key not found for deletion: key={}", idempotencyKey);
    }
  }
}
