package com.checkout.payment.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankResponse(
    boolean authorized,
    @JsonProperty("authorization_code") String authorizationCode
) {
  @Override
  public String toString(){
    return "BankResponse{" +
        "authorized=" + authorized +
        ",authorization_code=" + authorizationCode +
        "}";
  }
}
