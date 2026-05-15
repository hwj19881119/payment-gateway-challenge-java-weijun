package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.dto.BankRequest;
import com.checkout.payment.gateway.dto.BankResponse;
import com.checkout.payment.gateway.exception.BankServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
public class BankClientTest {

  @Mock
  private RestTemplate restTemplate;

  private BankClient bankClient;

  private static final String BANK_URL = "http://localhost:8080";
  private static final String BANK_PAYMENT_URL = BANK_URL + "/payments";

  @BeforeEach
  void setUp(){
    bankClient = new BankClient(restTemplate, BANK_URL);
  }

  private BankRequest createRequest(){
    return new BankRequest("12345678901234", "10/2028",
        "GBP", 1050L, "123");
  }

  @Test
  void whenBankAuthorizesThenAuthorizedResponseReturned(){
    BankResponse bankResponse = new BankResponse(
        true,
        "auth-code-12345"
    );
    when(restTemplate.postForObject(eq(BANK_PAYMENT_URL),
        any(BankRequest.class), eq(BankResponse.class)))
        .thenReturn(bankResponse);

    BankResponse result = bankClient.sendPayment(createRequest());

    assertTrue(result.authorized());
    assertEquals(bankResponse.authorizationCode(), result.authorizationCode());
  }

  @Test
  void whenBankDeclinedThenDeclinedResponseReturned(){
    BankResponse bankResponse = new BankResponse(false, null);
    when(restTemplate.postForObject(eq(BANK_PAYMENT_URL),
        any(BankRequest.class), eq(BankResponse.class)))
        .thenReturn(bankResponse);

    BankResponse result = bankClient.sendPayment(createRequest());

    assertFalse(result.authorized());
  }

  @Test
  void whenBankUnavailableThenBankServiceExceptionThrown(){
    when(restTemplate.postForObject(eq(BANK_PAYMENT_URL),
        any(BankRequest.class), eq(BankResponse.class)))
        .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    BankServiceException ex = assertThrows(BankServiceException.class,
        () -> bankClient.sendPayment(createRequest()));

    assertTrue(ex.getMessage().contains("Bank service unavailable"));
  }


  @Test
  void whenBankReturns4xxThenBankServiceExceptionThrown(){
    String invalid_cause = "Invalid payment request";
    when(restTemplate.postForObject(eq(BANK_PAYMENT_URL),
        any(BankRequest.class), eq(BankResponse.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, invalid_cause));

    BankServiceException ex = assertThrows(BankServiceException.class,
        () -> bankClient.sendPayment(createRequest()));

    assertTrue(ex.getMessage().contains("Bank service rejected request"));
    assertTrue(ex.getCause().getMessage().contains(invalid_cause));
  }

}
