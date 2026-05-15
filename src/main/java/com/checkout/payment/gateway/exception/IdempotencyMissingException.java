package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.dto.PostPaymentRequest;

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
