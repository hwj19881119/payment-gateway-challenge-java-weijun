package com.checkout.payment.gateway.dto;

public record ErrorResponse(String message) {

  public String toString() {
    return "ErrorResponse{" +
        "message='" + message + '\'' +
        '}';
  }
}
