package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.dto.BankRequest;
import com.checkout.payment.gateway.dto.BankResponse;
import com.checkout.payment.gateway.exception.BankServiceException;
import org.apache.juli.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(BankClient.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;


  public BankClient(RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankResponse sendPayment(BankRequest request){
    String paymentUrl = bankUrl + "/payments";
    LOG.debug("Sending payment to bank payment url: {}", paymentUrl);

    try{
      BankResponse response = restTemplate.postForObject(paymentUrl, request, BankResponse.class);
      LOG.info("Bank response received: {}", response != null ? response: "null");
      return response;
    }catch (HttpServerErrorException e){
      LOG.error("Bank returned server error: {}", e.getStatusCode(), e);
      throw new BankServiceException("Bank service unavailable: " + e.getStatusCode(), e);
    }catch (HttpClientErrorException e){
      LOG.error("Bank returned client error: {}", e.getStatusCode(), e);
      throw new BankServiceException("Bank service rejected request: " + e.getStatusCode(), e);
    }catch (ResourceAccessException e){
      LOG.error("Bank service failed to connect(timeout/refused).", e);
      throw new BankServiceException("Bank service unreachable", e);
    }


  }
}
