# Payment Gateway Service

A RESTful payment gateway service built with Spring Boot 3.1 and Java 17. It processes card payments by communicating with an acquiring bank simulator, stores transaction records, and supports idempotent request handling to safely support merchant retries.

## Table of Contents

- [Key Considerations & Assumptions](#key-considerations--assumptions)
- [Architecture Overview](#architecture-overview)
- [Payment Processing Flow](#payment-processing-flow)
- [Getting Started](#getting-started)
- [API Specifications](#api-specifications)
- [Status Code Summary](#status-code-summary)
- [Validation Rules](#validation-rules)
- [Test Strategy](#test-strategy)
- [Next Improvements](#next-improvements)

---

## Key Considerations & Assumptions

### PCI DSS Compliance (Logical Separation)

Full card numbers and CVVs are handled at the code and logic level only. The following rules are enforced:

- Full card number and CVV exist only in `PostPaymentRequest` during request processing. They are never persisted.
- Only the last 4 digits of the card number are stored in the repository and returned in any response.
- `toString()` on request/response objects omits full card number and CVV. All logging uses masked representations.
- CVV is used solely for the bank API call and is never stored or logged.

### Idempotency

Idempotency is handled via the `Idempotency-Key` HTTP header to prevent duplicate payments on merchant retries (e.g., network timeout). The lifecycle of an idempotency record:

```
New Request → IN_PROGRESS → Process Payment → COMPLETED (cached response)
                  │
                  ├── Validation fails → Record removed (key can be retried)
                  ├── Bank error       → Record stays IN_PROGRESS (key can be retried)
                  └── Concurrent request with same key → 409 Conflict
```

Completed keys return the cached response with HTTP 200 (not 201) to signal a replay. Keys used with a different request body return 409 Conflict.

### Out of Scope

| Item | Rationale |
|------|-----------|
| Merchant authentication/authorization | Not in requirements. A real system would use API keys or OAuth tokens scoped per merchant. |
| Idempotency key TTL/cleanup | No scheduled eviction implemented. Production should evict records older than a configurable TTL (e.g., 24 hours). |
| Persistent database | In-memory `ConcurrentHashMap` per requirements. A real system would use an ACID-compliant persistent store. |
| Rate limiting | Not in requirements. A production gateway would rate-limit per merchant. |
| HTTPS/TLS | Assumed to be handled by infrastructure (load balancer, reverse proxy). |

---

## Architecture Overview

```
┌──────────┐     HTTP POST/GET      ┌──────────────────┐     HTTP POST     ┌───────────────┐
│ Merchant │  ──────────────────>   │ Payment Gateway  │  ──────────────>  │ Bank Simulator│
│ (Client) │  <──────────────────   │  (port 8090)     │  <──────────────  │  (port 8080)  │
└──────────┘                        └──────────────────┘                   └───────────────┘
                                             │
                                             ▼
                                    ┌────────────────────┐
                                    │ In-Memory Store    │
                                    │ (ConcurrentHashMap)│
                                    └────────────────────┘
```

**Layered architecture:** Controller → Service (validation + idempotency + bank client) → Repository → Exception Handling (`@ControllerAdvice`).

## Payment Processing Flow

`POST /payments` is handled by `PaymentGatewayService.processPayment()` through the following steps:

```
Request → 1. Idempotency check → 2. Business validation → 3. Bank authorization → 4. Save payment → 5. Complete idempotency → 201
                 │                       │                        │
                 ├─ CACHED ───────────────────────────────────────────────────────────────────────────────────→ 200
                 ├─ IN_PROGRESS ──────→ 409
                 ├─ CONFLICT ─────────→ 409
                 │                       │
                 │                       └─ Validation fails → delete record → 422
                 │                                               │
                 │                           Bank error (503) ───└─ delete record → 502
```
**Step 0 Jakarta Bean Validation**

(`@Valid`) runs at the controller level before any of the below steps, returning 400 for structural/format issues.

**Step 1 — Idempotency check**

Call `idempotencyService.checkOrCreateRequest()` with the key and request. This returns one of four results:
- `CREATED` — new request, continue to step 2
- `CACHED` — this key was already completed, return the cached response with HTTP 200
- `IN_PROGRESS` — same key is currently being processed, throw `IdempotencyConflictException` → 409
- `CONFLICT` — same key used with a different request body, throw `IdempotencyConflictException` → 409

**Step 2 — Business validation**

Run `validator.validate(paymentRequest)`. If errors are found:
- Delete the `IN_PROGRESS` idempotency record (so the key can be retried with corrected data)
- Throw `PaymentRejectedException` → 422

**Step 3 — Bank authorization**

Build a `BankRequest` and call `bankClient.sendPayment()`. On `BankServiceException`:
- Delete the `IN_PROGRESS` idempotency record (so the key can be retried)
- Re-throw → 502

**Step 4 — Save payment**

Map the bank response to a `PaymentDetail` (status = `Authorized`/`Declined`, last 4 digits, etc.), save via
`paymentsRepository.add()`.

**Step 5 — Complete idempotency**

Mark the idempotency record as `COMPLETED` and cache the `PostPaymentResponse`. Return with HTTP 201.


---

## Getting Started

### Prerequisites

- JDK 17
- Docker (for the bank simulator)
- Gradle (wrapper included)

### 1. Start the Bank Simulator

```bash
docker-compose up -d
```

This starts the bank simulator on `http://localhost:8080`. Configuration is in `imposters/` — do not modify.

**Bank Simulator Test Cards:**

| Card Number | Last Digit | Result |
|-------------|------------|--------|
| `2222405343248877` | 7 (odd) | Authorized |
| `2222405343248878` | 8 (even) | Declined |
| `2222405343248870` | 0 | Service Unavailable (503) |

### 2. Start the Application

```
./gradlew bootRun --args='--spring.profiles.active=local'
```

The service starts on **port 8090** with the `local` profile (bank URL configured as `http://localhost:8080`).

### 3. Verify

```bash
# API documentation
open http://localhost:8090/swagger-ui/index.html

# Process a payment
curl -X POST http://localhost:8090/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-key-001" \
  -d '{
    "card_number": "2222405343248877",
    "expiry_month": 12,
    "expiry_year": 2030,
    "currency": "GBP",
    "amount": 1050,
    "cvv": "123"
  }'

# Retrieve a payment (use the id from the response above)
curl http://localhost:8090/payments/{id}
```

### 4. Run Tests

```
./gradlew test
```

---

## API Specifications

### POST /payments — Process a Payment

**Request:**

```
POST /payments
Content-Type: application/json
Idempotency-Key: <unique-key>

{
  "card_number": "2222405343248877",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "GBP",
  "amount": 1050,
  "cvv": "123"
}
```

| Field | Type | Required | Rules                                                       |
|-------|------|----------|-------------------------------------------------------------|
| `card_number` | String | Yes | 14–19 digits                                                |
| `expiry_month` | Integer | Yes | 1–12                                                        |
| `expiry_year` | Integer | Yes | Must not be in the past (combined with month)               |
| `currency` | String | Yes | Exactly 3 chars, one of: `GBP`, `USD`, `EUR`(test examples) |
| `amount` | Long | Yes | \> 0                                                        |
| `cvv` | String | Yes | 3–4 digits                                                  |

**Response 201 Created (Authorized):**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "Authorized",
  "card_number_last_four": "1111",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "GBP",
  "amount": 1050
}
```

**Response 201 Created (Declined by bank):**

```json
{
  "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "Declined",
  "card_number_last_four": "1111",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "GBP",
  "amount": 1050
}
```

**Response 200 OK (Idempotency replay — cached):**

Returns the same body as the original request with status code 200 (not 201) to signal a replayed response.
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "Authorized",
  "card_number_last_four": "1111",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "GBP",
  "amount": 1050
}
```

### GET /payments/{id} — Retrieve a Payment

**Request:**

```
GET /payments/{id}
```

**Response 200 OK:**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "Authorized",
  "card_number_last_four": "1111",
  "expiry_month": 12,
  "expiry_year": 2030,
  "currency": "GBP",
  "amount": 1050
}
```

---

## Status Code Summary

| HTTP Status | When                                                                                                                          | Response Body |
|-------------|-------------------------------------------------------------------------------------------------------------------------------|---------------|
| `201 Created` | Payment processed successfully (Authorized or Declined by bank)                                                               | `PostPaymentResponse` |
| `200 OK` | Payment retrieved, OR idempotency replay of a completed payment                                                               | `PostPaymentResponse` |
| `400 Bad Request` | Jakarta validation failure (missing/invalid fields), or missing `Idempotency-Key` header | `ValidationErrorResponse` |
| `404 Not Found` | Payment ID does not exist                                                                                                     | `ErrorResponse { "message": "payment not found" }` |
| `409 Conflict` | Same `Idempotency-Key` is already being processed, OR key was used with a different request body                              | `ErrorResponse` |
| `422 Unprocessable Entity` | Business validation failure (expired card, unsupported currency)                                                              | `ValidationErrorResponse { status: "Rejected", errors: [...] }` |
| `500 Internal Server Error` | Unexpected internal error                                                                                                     | `ErrorResponse { "message": "Internal server error" }` |
| `502 Bad Gateway` | Bank simulator returned 503 or is unreachable                                                                                 | `ErrorResponse` |

**400 vs 422 distinction:**
- **400** — structural/format issues caught by Jakarta validation (missing fields, wrong types, constraint violations)
- **422** — business rule violations caught by custom validator (expired card, unsupported currency)

---

## Validation Rules

Validation is enforced at two levels:

**Level 1 — Jakarta Bean Validation** (`@Valid` on controller, returns 400):

Handled by annotations on `PostPaymentRequest`: `@NotBlank`, `@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern`.

**Level 2 — Business Validation** (custom `PaymentRequestValidator`, returns 422):

| Rule | Error Message |
|------|---------------|
| Expiry date must be in the future (current month is invalid) | "Card has been expired." |
| Currency must be one of GBP, USD, EUR | "Currency must be in the allowed list" |

**Execution order:** Idempotency check → Business validation → Bank call. Jakarta validation runs at the controller level before any service logic.

---

## Test Strategy

```
          ┌──────────────┐
          │   E2E / Int  │  14 tests: full HTTP flow with MockMvc + real beans
          │   Tests      │
          ├──────────────┤
          │  Controller  │  10 tests: standalone MockMvc + mocked service
          │  Unit Tests  │
          ├──────────────┤
          │  Unit Tests  │  45+ tests: service, validator, idempotency,
          │              │  repository, bank client
          └──────────────┘
```

| Test Class | Type | Coverage                                                                        |
|------------|------|---------------------------------------------------------------------------------|
| `PaymentGatewayIntegrationTest` | Integration (`@SpringBootTest`) | Full flow: POST, GET, idempotency, validation, bank errors, concurrent requests |
| `PaymentGatewayControllerTest` | Controller unit (standalone MockMvc) | All exception handlers, HTTP status codes, response body structure              |
| `PaymentGatewayServiceTest` | Unit (Mockito) | Service logic: idempotency branches, validation, bank mapping, field mapping    |
| `IdempotencyServiceTest` | Unit (real objects) | Idempotency state transitions: CREATED, IN_PROGRESS, CACHED, CONFLICT, delete   |
| `PaymentRequestValidatorTest` | Unit | Jakarta annotation constraints on all fields and business validator check       |
| `BankClientTest` | Unit (`MockRestServiceServer`) | Bank HTTP calls: authorized, declined, unavailable                              |

Run all tests:

```
./gradlew test
```

---

## Next Improvements

The following improvements would be needed to make this a production-ready payment gateway:

### 4.1 Multi-Tenant Idempotency

`IdempotencyRecord` should include a `merchantId` field. Currently, all merchants share the same idempotency namespace, meaning two different merchants could collide on the same key. The composite key should be `(merchantId, idempotencyKey)`.


### 4.2 Configurable Currency Allowlist

The allowed currency list is hardcoded as `Set.of("USD", "GBP", "EUR")` in `PaymentRequestValidator`. This should be externalized to `application.properties` or a database table so it can be updated without redeployment:

```properties
payment.allowed-currencies=USD,GBP,EUR
```

### 4.3 Idempotency Key Cleanup on Bank Error

When business validation fails or the bank returns an error (`BankServiceException`), the `IN_PROGRESS` idempotency record is deleted so the merchant can retry with the same key. Currently there is no TTL-based cleanup for records that remain due to unexpected server crashes during processing. Production should implement a background job to evict stale `IN_PROGRESS` records older than a configurable TTL (e.g., 5 minutes).

### 4.4 PCI Service Separation

Split the application into PCI-scoped and non-PCI-scoped services:

- **PCI Service** — handles full card numbers and CVVs. Runs in an isolated environment with restricted access, audit logging, and HSM/KMS integration. Responsibilities:
  - Card tokenization vault (replace card numbers with tokens for recurring payments)
  - CVV cache with short TTL (for multi-step payment flows)
  - Secret key management via KMS (AWS KMS, HashiCorp Vault)
  - Encrypted database with PCI-compliant access controls

- **Non-PCI Service** — handles business logic using only masked card data (last 4 digits) and tokens. No card data ever enters this service.

### Additional Improvements

- **Merchant authentication** — API key or OAuth2 per merchant, with scoped access to only their own payments.
- **Idempotency TTL** — Background job to evict records older than a configurable TTL (e.g., 24 hours) to prevent unbounded memory growth.
- **Persistent storage** — Replace `ConcurrentHashMap` with a real database (PostgreSQL, DynamoDB) for durability across restarts.
- **Rate limiting** — Per-merchant rate limiting to prevent abuse.
- **Observability** — Structured logging, metrics (payment success/decline rates, latency), distributed tracing, and health checks.
- **Webhook notifications** — Notify merchants of async payment status changes instead of requiring polling.
- **Partial captures and refunds** — Support for post-authorization operations.
- **`RestTemplate` migration** — Migrate to `RestClient` (Spring 6.1+) as `RestTemplate` is in maintenance mode.
- **HTTPS/TLS** — Enforce HTTPS at the application level, not just infrastructure.
- **Request/response logging filter** — Add a Spring filter for structured HTTP access logs with correlation IDs.
