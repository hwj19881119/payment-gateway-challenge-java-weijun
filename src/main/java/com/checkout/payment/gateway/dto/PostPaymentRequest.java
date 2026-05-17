package com.checkout.payment.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PostPaymentRequest(

    @NotBlank(message = "Card number is required")
    @Size(min=14, max=19, message="Card Number must be between 14-19 characters long")
    @Pattern(regexp = "\\d+", message="Card number must only contain numeric characters")
    @JsonProperty("card_number")
    String cardNumber,

    @NotNull(message = "Expiry month is required")
    @Min(value=1, message = "Expiry month must be between 1 and 12")
    @Max(value=12, message = "Expiry month must be between 1 and 12")
    @JsonProperty("expiry_month")
    Integer expiryMonth,

    @NotNull(message = "Expiry year is required")
    @Min(value=1, message = "Expiry year is required")
    @JsonProperty("expiry_year")
    Integer expiryYear,

    @NotBlank(message = "Currency is required")
    @Size(min=3, max=3, message = "Currency must be 3 characters")
    String currency,

    @NotNull(message = "Amount is required")
    @Min(value=1, message="Amount must be greater than 0")
    Long amount,

    @NotBlank(message = "CVV is required")
    @Size(min=3, max=4, message="CVV must be 3-4 characters long")
    @Pattern(regexp = "\\d+", message = "CVV must only contain numeric characters")
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
        "cardNumberLastFour='%s'".formatted(getCardNumberLastFour()) +
        ", expiryMonth=%d".formatted(expiryMonth) +
        ", expiryYear=%d".formatted(expiryYear) +
        ", currency='%s'".formatted(currency) +
        ", amount=%d".formatted(amount) +
        '}';
  }
}
