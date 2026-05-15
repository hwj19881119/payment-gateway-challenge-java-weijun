package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.enums.IdempotencyStatus;
import java.time.Instant;
import java.util.UUID;

public class IdempotencyRecord {

  private UUID id;
  private String idempotencyKey;
  private IdempotencyStatus status;
  private PostPaymentResponse cachedResponse;
  private String requestHash;
  private Instant createdAt;
  private Instant updatedAt;

  public IdempotencyRecord(){}

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public IdempotencyStatus getStatus() {
    return status;
  }

  public void setStatus(IdempotencyStatus status) {
    this.status = status;
  }

  public PostPaymentResponse getCachedResponse() {
    return cachedResponse;
  }

  public void setCachedResponse(PostPaymentResponse cachedResponse) {
    this.cachedResponse = cachedResponse;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
