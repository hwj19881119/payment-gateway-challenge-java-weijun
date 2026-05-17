package com.checkout.payment.gateway.dto;

import org.springframework.http.HttpStatus;

public record PostPaymentResponseWithStatus(
    PostPaymentResponse postPaymentResponse,
    HttpStatus httpStatus
) {

    public static PostPaymentResponseWithStatus newResponse(PostPaymentResponse response){
      return new PostPaymentResponseWithStatus(response, HttpStatus.CREATED);
    }

    public static PostPaymentResponseWithStatus cachedResponse(PostPaymentResponse response){
      return new PostPaymentResponseWithStatus(response, HttpStatus.OK);
    }
}
