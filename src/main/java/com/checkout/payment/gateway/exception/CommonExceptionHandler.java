package com.checkout.payment.gateway.exception;

import com.checkout.payment.gateway.dto.ErrorResponse;
import com.checkout.payment.gateway.dto.ValidationErrorResponse;
import com.checkout.payment.gateway.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CommonExceptionHandler.class);

  @ExceptionHandler(EventProcessingException.class)
  public ResponseEntity<ErrorResponse> handleException(EventProcessingException ex) {
    LOG.error("Exception happened", ex);
    return new ResponseEntity<>(new ErrorResponse("Page not found"),
        HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<ValidationErrorResponse> handleRejected(PaymentRejectedException ex){
    LOG.warn("Payment rejected: {}", ex.getErrors());
    return new ResponseEntity<>(
        new ValidationErrorResponse(PaymentStatus.REJECTED, ex.getErrors()),
        HttpStatus.UNPROCESSABLE_ENTITY
    );
  }

  @ExceptionHandler(BankServiceException.class)
  public ResponseEntity<ErrorResponse> handleBankError(BankServiceException ex){
    LOG.warn("Bank service error.", ex);
    return new ResponseEntity<>(
        new ErrorResponse("Payment processing failed due to bank service error:" + ex.getMessage()),
        HttpStatus.BAD_GATEWAY
    );
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException ex){
    LOG.warn("IdempotencyKey conflict: key={}, reason={}", ex.getIdempotencyKey(), ex.getMessage());
    return new ResponseEntity<>(
        new ErrorResponse(ex.getMessage()),
        HttpStatus.CONFLICT
    );
  }

  @ExceptionHandler(IdempotencyMissingException.class)
  public ResponseEntity<ErrorResponse> handleIdempotencyMissing(IdempotencyMissingException ex){
    LOG.warn("Missing idempotency key: {}", ex.getMessage());
    return new ResponseEntity<>(
        new ErrorResponse(ex.getMessage()),
        HttpStatus.BAD_REQUEST
    );
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(Exception ex){
    LOG.error("Unexpected error", ex);
    return new ResponseEntity<>(
        new ErrorResponse("Internal server error"),
        HttpStatus.INTERNAL_SERVER_ERROR
    );
  }


}
