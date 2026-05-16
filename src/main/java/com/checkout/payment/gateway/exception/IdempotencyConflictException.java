package com.checkout.payment.gateway.exception;

public class IdempotencyConflictException extends RuntimeException{

  private final String idempotencyKey;

  public IdempotencyConflictException(String message, String idempotencyKey){
    super("IdempotencyKey conflicted:key=%s, message=%s".formatted(idempotencyKey, message));
    this.idempotencyKey = idempotencyKey;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }
}
