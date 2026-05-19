package com.checkout.payment.gateway.dto;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record PostPaymentResponse(
    UUID id,
    PaymentStatus status,
    @JsonProperty("card_number_last_four") String cardNumberLastFour,
    @JsonProperty("expiry_month") int expiryMonth,
    @JsonProperty("expiry_year") int expiryYear,
    String currency,
    Long amount
) {

  @Override
  public String toString() {
    return "PostPaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
