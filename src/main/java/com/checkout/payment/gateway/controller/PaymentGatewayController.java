package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.dto.PostPaymentRequest;
import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.dto.PostPaymentResponseWithStatus;
import com.checkout.payment.gateway.exception.IdempotencyMissingException;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;
  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayController.class);

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  @PostMapping
  public ResponseEntity<PostPaymentResponse> processPayment(
      @RequestHeader(value="Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PostPaymentRequest request
  ){
    if(idempotencyKey == null || idempotencyKey.isBlank()){
      throw new IdempotencyMissingException("POST /payments");
    }

    LOG.debug("POST /payments received, idempotency key: {}, request: {}",
        idempotencyKey, request.toString());

    PostPaymentResponseWithStatus response = paymentGatewayService.processPayment(idempotencyKey, request);
    LOG.debug("POST payment response received: status={}, response={}", response.httpStatus(), response.postPaymentResponse().toString());
    return new ResponseEntity<>(response.postPaymentResponse(), response.httpStatus());

  }

  @GetMapping("/{id}")
  public ResponseEntity<PostPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
