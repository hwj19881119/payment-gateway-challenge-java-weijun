package com.checkout.payment.gateway.exception;

public class IdempotencyMissingException extends RuntimeException{

  private final String requestSummary;
  public IdempotencyMissingException(String requestSummary){
    super("Idempotency-Key header is required: " + requestSummary);
    this.requestSummary = requestSummary;
  }

  public String getRequestSummary() {
    return requestSummary;
  }
}
