# Resilience

What this application promises when a dependency fails.
Fault tests, and the agents that write them, check the code against these promises; the code alone cannot say what they are.
Keep it short and keep it current: a promise that changes in the code changes here too.

## What healthy means

<!-- The requests that must keep working, and how well, for the application to count as up. -->

- `POST /orders` answers `201` within 500 ms.
- `GET /health/ready` answers `200`.

## Dependencies and what protects them

<!-- One row per target. The dependency is what a test passes to target(...). -->

| Dependency | Called from | Protection | Expected behaviour when it fails |
|---|---|---|---|
| `target(InventoryClient.class)` | `InventoryService.reserve` | `@Timeout(1000)`, `@CircuitBreaker`, `@Fallback` | `POST /orders` answers `503 inventory unavailable` within 1.5 s; no order is stored |
| `target(DataSource.class)` | `OrderRepository` | Agroal `acquisition-timeout=2s`, JDBC `socketTimeout=2` | `POST /orders` answers `503`; the transaction is rolled back |
| `target(PaymentClient::charge)` | `PaymentService.charge` | `@Retry(maxRetries = 2)` | Retried twice with the same idempotency key, then `502` |

## Promises to verify

<!-- Statements a test can check. Each one names the fault that would break it. -->

- An order is never stored without a reservation, whichever way the inventory service fails.
- No request waits longer than 2 s for any dependency.
- A failed payment is never charged twice.

## Out of bounds

<!-- What experiments and tests must not do. -->

- Do not apply faults to `rest-client:payments` in dev mode: it points at a shared sandbox.
- Do not change timeouts or annotations to make a test pass; report the behaviour instead.
