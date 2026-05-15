package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.enums.IdempotencyStatus;
import java.time.Instant;

public class IdempotencyRecord {

  private String idempotencyKey;
  private IdempotencyStatus status;
  private PostPaymentResponse cachedResponse;
  private String requestHash;
  private Instant createdAt;

  public IdempotencyRecord(){}

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
}
