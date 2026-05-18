package com.checkout.payment.gateway.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.enums.IdempotencyStatus;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.IdempotencyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;


public class IdempotencyRecordRepositoryTest {

  private IdempotencyRepository repository;

  @BeforeEach
  void setUp(){
    repository = new InMemoryIdempotencyRecordRepository();
  }

  @Test
  void whenAddedThenItCanBeRetrieved(){
    IdempotencyRecord record = new IdempotencyRecord();
    record.setIdempotencyKey("KEY_1");
    record.setStatus(IdempotencyStatus.IN_PROGRESS);
    record.setRequestHash("MOCKED_HASH");

    IdempotencyRecord savedRecord = repository.save(record);
    assertEquals(savedRecord.getIdempotencyKey(), record.getIdempotencyKey());
    assertEquals(savedRecord.getStatus(), record.getStatus());
    assertEquals(savedRecord.getRequestHash(), record.getRequestHash());
    assertNotNull(savedRecord.getId());
    assertNotNull(savedRecord.getCreatedAt());
    assertNotNull(savedRecord.getUpdatedAt());

    Optional<IdempotencyRecord> retrieved = repository.get("KEY_1");
    assertTrue(retrieved.isPresent());
    IdempotencyRecord result = retrieved.get();
    assertEquals(result.getIdempotencyKey(), savedRecord.getIdempotencyKey());
    assertEquals(result.getStatus(), savedRecord.getStatus());
    assertEquals(result.getRequestHash(), savedRecord.getRequestHash());
  }

  @Test
  void whenGetNonExistentKeyThenReturnsEmpty(){
    Optional<IdempotencyRecord> shouldBeEmpty = repository.get("no-such-key");
    assertTrue(shouldBeEmpty.isEmpty());
  }

  @Test
  void whenRemovedThenKeyNoLongerExists(){
    String idempotencyKey = "KEY_1";

    IdempotencyRecord record = new IdempotencyRecord();
    record.setIdempotencyKey(idempotencyKey);
    record.setStatus(IdempotencyStatus.IN_PROGRESS);
    record.setRequestHash("MOCKED_HASH");

    repository.save(record);
    assertTrue(repository.get(idempotencyKey).isPresent());

    repository.delete(idempotencyKey);
    assertTrue(repository.get(idempotencyKey).isEmpty());

  }

  @Test
  void whenUpdatedThenResponseIsChanged(){
    String idempotencyKey = "KEY_1";

    IdempotencyRecord record = new IdempotencyRecord();
    record.setIdempotencyKey(idempotencyKey);
    record.setStatus(IdempotencyStatus.IN_PROGRESS);
    record.setRequestHash("MOCKED_HASH");

    repository.save(record);

    Optional<IdempotencyRecord> retrieved = repository.get(idempotencyKey);
    assertTrue(retrieved.isPresent());

    IdempotencyRecord needToUpdate = retrieved.get();
    needToUpdate.setStatus(IdempotencyStatus.COMPLETED);
    PostPaymentResponse response = new PostPaymentResponse(
        UUID.randomUUID(), PaymentStatus.DECLINED, "1234",
        10, 2028, "USD", 5000L
    );
    needToUpdate.setCachedResponse(response);
    repository.save(needToUpdate);

    Optional<IdempotencyRecord> updated = repository.get(idempotencyKey);
    assertTrue(updated.isPresent());
    assertEquals(IdempotencyStatus.COMPLETED, updated.get().getStatus());
    assertEquals(PaymentStatus.DECLINED, updated.get().getCachedResponse().status());
    assertEquals("1234", updated.get().getCachedResponse().cardNumberLastFour());
  }

}
