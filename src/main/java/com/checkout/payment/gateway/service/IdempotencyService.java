package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.dto.PostPaymentRequest;
import com.checkout.payment.gateway.dto.PostPaymentResponse;
import com.checkout.payment.gateway.enums.IdempotencyStatus;
import com.checkout.payment.gateway.model.IdempotencyRecord;
import com.checkout.payment.gateway.repository.IdempotencyRepositoryInterface;
import com.checkout.payment.gateway.service.IdempotencyService.IdempotencyCheckResult.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

  private final IdempotencyRepositoryInterface idempotencyRepository;
  private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);

  public IdempotencyService(IdempotencyRepositoryInterface idempotencyRepository) {
    this.idempotencyRepository = idempotencyRepository;
  }

  public IdempotencyCheckResult checkOrCreateRequest(String idempotencyKey, PostPaymentRequest request){
    String requestHash = generateRequestHash(request, idempotencyKey);
    LOG.debug("request hash: key={}, request={}, hash={}", idempotencyKey, request, requestHash);

    Optional<IdempotencyRecord> ret = idempotencyRepository.get(idempotencyKey);


    if(ret.isEmpty()){
      IdempotencyRecord record = new IdempotencyRecord();
      record.setIdempotencyKey(idempotencyKey);
      record.setStatus(IdempotencyStatus.IN_PROGRESS);
      record.setRequestHash(requestHash);
      idempotencyRepository.save(record);
      LOG.debug("Idempotency check: create new, IN_PROGRESS record for key={}", idempotencyKey);
      return new IdempotencyCheckResult(Type.CREATED, null);
    }
    IdempotencyRecord record = ret.get();

    if(!requestHash.equals(record.getRequestHash())){
      LOG.debug("Idempotency check: hash mismatch(conflict) for key={}", idempotencyKey);
      return new IdempotencyCheckResult(Type.CONFLICT, null);
    }

    if(record.getStatus() == IdempotencyStatus.IN_PROGRESS){
      LOG.debug("Idempotency check: request is IN_PROGRESS for key={}", idempotencyKey);
      return new IdempotencyCheckResult(Type.IN_PROGRESS, null);
    }

    if(record.getStatus() == IdempotencyStatus.COMPLETED){
      LOG.debug("Idempotency check: same request is completed. returning cached response.");
      return new IdempotencyCheckResult(Type.CACHED, record.getCachedResponse());
    }

    LOG.error("Unexpected idempotency status: key={}, status={}",
        record.getIdempotencyKey(), record.getStatus());
    throw new RuntimeException("Unexpected idempotency status: key=%s, status=%s"
        .formatted(record.getIdempotencyKey(), record.getStatus()));

  }

  public void deleteRequest(String idempotencyKey){
    LOG.info("Idempotency delete: key={}", idempotencyKey);
    idempotencyRepository.delete(idempotencyKey);
  }

  public void completeRequest(String idempotencyKey, PostPaymentResponse response){
    Optional<IdempotencyRecord> existing = idempotencyRepository.get(idempotencyKey);
    if(existing.isPresent()){
      IdempotencyRecord record = existing.get();
      record.setStatus(IdempotencyStatus.COMPLETED);
      record.setCachedResponse(response);
      idempotencyRepository.save(record);
      LOG.debug("Idempotency complete: key={}, paymentId={}", idempotencyKey, response.id());
    }else{
      LOG.error("Idempotency complete error: failed to find the idempotency key:{}", idempotencyKey);
      throw new RuntimeException(
          "Failed to find idempotency key=%s".formatted(idempotencyKey));
    }
  }

  private String generateRequestHash(PostPaymentRequest request, String idempotencyKey){
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(request.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      LOG.error("Request Hash generation failed: key={}", idempotencyKey, e);
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  public record IdempotencyCheckResult(
      Type type,
      PostPaymentResponse cachedResponse
  ){
    public enum Type {CREATED, IN_PROGRESS, CONFLICT, CACHED}
  }

}
