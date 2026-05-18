package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.dto.BankRequest;
import com.checkout.payment.gateway.dto.BankResponse;
import com.checkout.payment.gateway.dto.PostPaymentResponseWithStatus;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankServiceException;
import com.checkout.payment.gateway.exception.IdempotencyConflictException;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.dto.PostPaymentRequest;
import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PaymentDetail;
import com.checkout.payment.gateway.repository.PaymentsRepositoryInterface;
import java.util.List;
import java.util.UUID;
import com.checkout.payment.gateway.service.IdempotencyService.IdempotencyCheckResult;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepositoryInterface paymentsRepository;
  private final IdempotencyService idempotencyService;
  private final PaymentRequestValidator validator;
  private final BankClient bankClient;

  public PaymentGatewayService(PaymentsRepositoryInterface paymentsRepository,
      IdempotencyService idempotencyService, PaymentRequestValidator validator, BankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.idempotencyService = idempotencyService;
    this.validator = validator;
    this.bankClient = bankClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    PaymentDetail payment = paymentsRepository.get(id)
        .orElseThrow(() -> new PaymentNotFoundException("Invalid Payment ID: %s".formatted(id)));
    return new PostPaymentResponse(
        payment.getId(),
        payment.getStatus(),
        payment.getCardNumberLastFour(),
        payment.getExpiryMonth(),
        payment.getExpiryYear(),
        payment.getCurrency(),
        payment.getAmount()
    );
  }


  public PostPaymentResponseWithStatus processPayment(String idempotencyKey, PostPaymentRequest paymentRequest) {
    LOG.info("Processing payment for key={} and request={}", idempotencyKey, paymentRequest);

    // step 1: check request idempotency status
    IdempotencyCheckResult idempotencyResult =
        idempotencyService.checkOrCreateRequest(idempotencyKey, paymentRequest);

    // continue if the request is new
    switch (idempotencyResult.type()){
      case IN_PROGRESS:
        LOG.debug("Idempotency result: request is still in processing, key={}", idempotencyKey);
        throw new IdempotencyConflictException(
            "Payment with this idempotency key is already in process.", idempotencyKey);
      case CONFLICT:
        LOG.warn("Idempotency result: request is conflict for key={}", idempotencyKey);
        throw new IdempotencyConflictException(
            "Idempotency key has already been used with a different body", idempotencyKey);
      case CACHED:
        LOG.warn("Idempotency result: request has been cached for key={}", idempotencyKey);
        // return with status code?
        return PostPaymentResponseWithStatus.cachedResponse(idempotencyResult.cachedResponse());
      case CREATED:
        LOG.debug("Idempotency result: new request, continue for next step");
        break;
    }

    // step 2: validate business rules
    List<String> errors = validator.validate(paymentRequest);
    if(!errors.isEmpty()){
      LOG.warn("Payment validation failed: {}", errors);

      // remove invalid request to prevent new request in stuck,
      // but it might not be necessary if new idempotencyKey with new request
      idempotencyService.deleteRequest(idempotencyKey);
      throw new PaymentRejectedException(errors);
    }

    // step 3: authorize the payment
    BankRequest bankRequest = new BankRequest(
        paymentRequest.cardNumber(),
        paymentRequest.getExpiryDate(),
        paymentRequest.currency(),
        paymentRequest.amount(),
        paymentRequest.cvv()
    );

    try{
      BankResponse bankResponse = bankClient.sendPayment(bankRequest);
      PaymentDetail payment = toPaymentDetail(paymentRequest, bankResponse);
      PaymentDetail savedPayment = paymentsRepository.add(payment);

      LOG.info("payment is saved with id={}, status={}, card=****{}",
          savedPayment.getId(), savedPayment.getStatus(), savedPayment.getCardNumberLastFour());

      PostPaymentResponse response = new PostPaymentResponse(
          savedPayment.getId(),
          savedPayment.getStatus(),
          savedPayment.getCardNumberLastFour(),
          savedPayment.getExpiryMonth(),
          savedPayment.getExpiryYear(),
          savedPayment.getCurrency(),
          savedPayment.getAmount()
      );

      // step 4: set request idempotency record to COMPLETE and cache the response
      completeIdempotency(idempotencyKey, response);
      return PostPaymentResponseWithStatus.newResponse(response);

    }catch(BankServiceException e){
      LOG.warn("bank service exception: {}", e.getMessage());
//      // TODO: judge to delete request from idempotency to support retrying
//      idempotencyService.deleteRequest(idempotencyKey);
      throw e;
    }



  }

  private PaymentDetail toPaymentDetail(PostPaymentRequest paymentRequest,
      BankResponse bankResponse) {
    PaymentDetail payment = new PaymentDetail();
    payment.setStatus(bankResponse.authorized() ? PaymentStatus.AUTHORIZED: PaymentStatus.DECLINED);
    payment.setCardNumberLastFour(paymentRequest.getCardNumberLastFour());
    payment.setExpiryMonth(paymentRequest.expiryMonth());
    payment.setExpiryYear(paymentRequest.expiryYear());
    payment.setCurrency(paymentRequest.currency());
    payment.setAmount(paymentRequest.amount());
    payment.setAuthorizationCode(bankResponse.authorizationCode());
    return payment;
  }

  private void completeIdempotency(String idempotencyKey, PostPaymentResponse response){
    try{
      idempotencyService.completeRequest(idempotencyKey, response);
    }catch (Exception e){
      LOG.error("Failed to complete the idempotency request", e);
    }
  }


}
