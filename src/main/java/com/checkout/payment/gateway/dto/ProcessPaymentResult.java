package com.checkout.payment.gateway.dto;

public record ProcessPaymentResult(PostPaymentResponse response, boolean cached) {

  public static ProcessPaymentResult newPayment(PostPaymentResponse response) {
    return new ProcessPaymentResult(response, false);
  }

  public static ProcessPaymentResult cachedPayment(PostPaymentResponse response) {
    return new ProcessPaymentResult(response, true);
  }
}
