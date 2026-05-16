package com.checkout.payment.gateway.validator;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.checkout.payment.gateway.dto.PostPaymentRequest;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;

public class PaymentRequestValidatorTest {

  private PaymentRequestValidator businessValidator;
  private Validator jakartaValidator;


  @BeforeEach
  void setUp(){
    businessValidator = new PaymentRequestValidator();
    jakartaValidator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  // Jakarta Bean Validation Tests

  /**
   * helper function to check violation field and reason matches
   * @param violations: jakarta violations
   * @param field: field name
   * @param reason: keyword in wrong messages
   * @return Ture if field is correct and message contains keyword
   */
  private boolean hasFieldViolation(
      Set<ConstraintViolation<PostPaymentRequest>> violations, String field, String reason){
    return violations.stream()
        .anyMatch(
            v -> v.getPropertyPath().toString().equals(field)
                && v.getConstraintDescriptor().toString().contains(reason)
        );
  }

  @Test
  void validRequestNoViolation(){

    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        12,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertTrue(violations.isEmpty());
  }

  // ---- card number tests -----
  @Test
  void cardNumberBlankOneViolation(){
    PostPaymentRequest errorRequest = new PostPaymentRequest(
      "",
      12,
      2028,
      "USD",
      1000L,
      "123"
    );

    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(errorRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cardNumber", "required"));

  }

  @Test
  void cardNumberNullOneViolation(){
    PostPaymentRequest errorRequest = new PostPaymentRequest(
        null,
        12,
        2028,
        "USD",
        1000L,
        "123"
    );

    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(errorRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cardNumber", "required"));

  }

  @Test
  void cardNumberTooShortViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234",
        12,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cardNumber", "between 14-19"));
  }

  @Test
  void cardNumberTooLongViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "12345678901234567890", // 20 character long
        12,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cardNumber", "between 14-19"));
  }

  @Test
  void cardNumberNonNumericViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890abcdef",
        12,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cardNumber", "numeric"));
  }

  // ---- expiry month tests -----
  @Test
  void expiryMonthNullViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        null,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "expiryMonth", "required"));

  }

  @Test
  void expiryMonthTooSmallViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        0,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "expiryMonth", "between 1 and 12"));

  }


  @Test
  void expiryMonthTooLargeViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        14,
        2028,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "expiryMonth", "between 1 and 12"));

  }


  // ---- expiry year tests -----
  @Test
  void expiryYearNullViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        null,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "expiryYear", "required"));

  }

  @Test
  void expiryYearTooSmallViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        0,
        "USD",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "expiryYear", "required"));

  }

  // ------ expiry date business tests ------
  @Test
  void expiryDateNotInFutureValiation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2020,
        "USD",
        1000L,
        "123"
    );

    List<String> errors = businessValidator.validate(validRequest);
    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("expired")));
  }

  // ------- currency tests ------
  @Test
  void currencyBlankViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "",
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "currency", "required"));
  }

  @Test
  void currencySizeViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USDX", // 4 characters
        1000L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "currency", "3 characters"));
  }

  @Test
  void currencyWhitelistViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "YYY", // not in the list, mocked currency
        1000L,
        "123"
    );
    List<String> errors = businessValidator.validate(validRequest);
    assertFalse(errors.isEmpty());
    assertTrue(errors.stream().anyMatch(e -> e.contains("allowed list")));

  }

  // ------ amount tests -------
  @Test
  void amountNullViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        null,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "amount", "required"));
  }

  @Test
  void amountMinusViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        -1L,
        "123"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "amount", "greater than 0"));
  }

  // ----- cvv tests ----
  @Test
  void cvvBlankViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        1000L,
        ""
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cvv", "required"));
  }

  @Test
  void cvvNullViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        1000L,
        null
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cvv", "required"));
  }

  @Test
  void cvvTooShortViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        1000L,
        "1"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cvv", "3-4 characters long"));
  }


  @Test
  void cvvTooLongViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        1000L,
        "123456"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cvv", "3-4 characters long"));
  }


  @Test
  void cvvNonNumericViolation(){
    PostPaymentRequest validRequest = new PostPaymentRequest(
        "1234567890123456",
        10,
        2028,
        "USD", // 4 characters
        1000L,
        "1a2"
    );
    Set<ConstraintViolation<PostPaymentRequest>> violations = jakartaValidator.validate(validRequest);
    assertFalse(violations.isEmpty());
    assertTrue(hasFieldViolation(violations, "cvv", "numeric characters"));
  }



}
