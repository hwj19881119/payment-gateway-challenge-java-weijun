package com.checkout.payment.gateway.dto;

import com.checkout.payment.gateway.enums.PaymentStatus;
import java.util.List;

public record ValidationErrorResponse(
    PaymentStatus status,
    List<String> errors
) {

  @Override
  public String toString(){
    return "ValidationErrorResponse{status=}" + status +",errors=" + errors + "}";
  }

}
