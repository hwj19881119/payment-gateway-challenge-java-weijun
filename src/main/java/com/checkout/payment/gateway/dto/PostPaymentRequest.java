package com.checkout.payment.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PostPaymentRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_month") Integer expiryMonth,
    @JsonProperty("expiry_year") Integer expiryYear,
    String currency,
    Long amount,
    String cvv
) {

  public String getCardNumberLastFour(){
    return cardNumber.length() >= 4 ?
        cardNumber.substring(cardNumber.length() - 4): cardNumber;
  }

  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "PostPaymentRequest{" +
        "cardNumberLastFour=" + getCardNumberLastFour() +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
