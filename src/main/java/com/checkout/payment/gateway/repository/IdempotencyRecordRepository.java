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
public class IdempotencyRecordRepository {

  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyRecordRepository.class);

  private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

  public IdempotencyRecord save(IdempotencyRecord record){
    if(record.getId() == null){
      record.setId(UUID.randomUUID());
      record.setCreatedAt(Instant.now());
    }
    record.setUpdatedAt(Instant.now());;
    records.put(record.getIdempotencyKey(), record);
    LOG.debug("Idempotency key save: {}, status: {}", record.getIdempotencyKey(), record.getStatus());
    return record;
  }

  public Optional<IdempotencyRecord> get(String idempotencyKey){
    return Optional.ofNullable(records.get(idempotencyKey));
  }


  public void delete(String idempotencyKey){
    LOG.debug("Idempotency key deleted: key={}", idempotencyKey);
    if(records.containsKey(idempotencyKey)){
      records.remove(idempotencyKey);
    }else{
      LOG.warn("idempotency key missed: key={}", idempotencyKey);
    }
  }
}
