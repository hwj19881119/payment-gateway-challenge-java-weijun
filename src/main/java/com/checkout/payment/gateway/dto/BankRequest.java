package com.checkout.payment.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankRequest(
    @JsonProperty("card_number") String cardNumber,
    @JsonProperty("expiry_date") String expiryDate,
    String currency,
    Long amount,
    String cvv
) {

  public String getCardNumberFour(){
    return cardNumber.length() >= 4 ?
        cardNumber.substring(cardNumber.length() - 4): cardNumber;
  }

  @Override
  public String toString(){
    return "BankRequest{" +
        "cardNumberLastFour=" + getCardNumberFour() +
        ",expiry_date=" + expiryDate +
        ",currency=" + currency +
        ",amount=" + amount +
        "}";
  }
}
