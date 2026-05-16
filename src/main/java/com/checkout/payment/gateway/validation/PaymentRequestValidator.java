package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.dto.PostPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PaymentRequestValidator {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentRequestValidator.class);

  // TODO: Temporary implementation to get allowed currencies
  private static final Set<String> ALLOWED_CURRENCIES = Set.of("USD", "GBP", "EUR");

  /**
   * validate business rules for payment request
   * @param request
   * @return list of validation error messages (empty = valid)
   */
  public List<String> validate(PostPaymentRequest request){

    List<String> errors = new ArrayList<>();

    errors.addAll(validateExpiryInFuture(request.expiryMonth(), request.expiryYear()));
    errors.addAll(validateCurrencyWhitelist(request.currency()));
    return errors;

  }

  /**
   * validate whether expiry date is in the future
   * @param expiryMonth
   * @param expiryYear
   * @return list of violation error messages
   */
  private List<String> validateExpiryInFuture(int expiryMonth, int expiryYear){
    List<String> errors = new ArrayList<>();
    YearMonth expiry = YearMonth.of(expiryYear, expiryMonth);
    if(!expiry.isAfter(YearMonth.now())){
      errors.add("Card has been expired");
    }
    return errors;
  }

  /**
   * validate whether currency is in the allowed list
   * @param currency
   * @return list of violation error messages
   */
  private List<String> validateCurrencyWhitelist(String currency){
    List<String> errors = new ArrayList<>();
    if(!ALLOWED_CURRENCIES.contains(currency)){
      errors.add("Currency must be in the allowed list");
    }
    return errors;
  }

}
