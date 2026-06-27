# OrderFlow Architecture

OrderFlow is a containerized order workflow system with a Spring Boot API process, Spring Boot worker process, PostgreSQL persistence, Redis idempotency cache, Redpanda/Kafka-compatible event broker, and React TypeScript operations console.

## Runtime View

```mermaid
flowchart LR
    user["Operator / developer"] --> console["React operations console"]
    console --> api["order-api Spring Boot REST API"]
    api --> checkout["Checkout session / payment attempt"]
    api --> postgres["PostgreSQL"]
    api --> redis["Redis idempotency cache"]
    api --> outbox["Transactional outbox table"]
    outbox --> worker["order-worker"]
    worker --> publisher["Outbox publisher"]
    publisher --> broker["Redpanda / Kafka topic"]
    broker --> consumer["Order event consumer"]
    consumer --> postgres
    consumer --> dlq["Dead-letter records"]
    console --> sse["SSE health stream"]
    sse --> api
```

The backend codebase remains one Spring Boot application, but it can now run as two runtime roles. `order-api` owns REST, console, and SSE endpoints. `order-worker` owns asynchronous outbox publishing, Kafka consumption, retry, and DLQ recovery. Order, inventory, payment simulation, audit, outbox, event processing, DLQ, operations health, and realtime endpoints still keep their own package and persistence boundaries.

## Order State Flow

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT
    PENDING_PAYMENT --> CREATED
    PENDING_PAYMENT --> EXPIRED
    PENDING_PAYMENT --> CANCELLED
    CREATED --> INVENTORY_RESERVED
    INVENTORY_RESERVED --> PAYMENT_AUTHORIZED
    PAYMENT_AUTHORIZED --> COMPLETED
    CREATED --> FAILED
    INVENTORY_RESERVED --> FAILED
    CREATED --> CANCELLED
    INVENTORY_RESERVED --> CANCELLED
```

The state machine keeps workflow transitions explicit. Audit log entries make each state change visible to the order timeline and operations console.

## Checkout And Payment Boundary

The operations console now uses a checkout-session flow instead of creating an order directly from the first button click. Starting checkout creates a 15-minute `checkout_session`, a stable `PENDING_PAYMENT` order, and one active business payment attempt. Refreshing the console restores the same checkout session from `localStorage` and `GET /api/checkout-sessions/{id}` instead of generating a new payment idempotency key in the browser.

The payment model separates four identifiers:

- `orderId`: the long-lived business order identity.
- `paymentAttemptId`: the business identity for the current simulated authorization attempt.
- `idempotencyKey`: the backend-derived duplicate-protection key for the authorize operation, currently `authorize:{paymentAttemptId}`.
- `requestAttemptId`: the observability identity for each physical confirm HTTP call or retry.

PostgreSQL is the source of truth for payment attempts and request-attempt logs. The request hash is stored as an opaque SHA-256 fingerprint on first logical confirm; deeper provider-payload conflict policy is intentionally left for a future real payment-provider integration. Payment method switching, failed-payment retry orchestration, and real payment gateways are also outside the current implementation.

Confirming checkout transitions the order from `PENDING_PAYMENT` to `CREATED`, marks the payment attempt `AUTHORIZED`, records the physical request attempt, and writes the `ORDER_CREATED` outbox event in the same transaction. A repeated confirm for the same payment attempt records a new request attempt as `REPLAYED` but does not create another business payment attempt or outbox event. A simulated gateway response timeout records the request attempt as `TIMEOUT`, keeps the business payment attempt `INITIATED`, and lets the next confirm retry reuse the same backend-derived idempotency key.

## Outbox And Recovery Flow

```mermaid
sequenceDiagram
    participant API as Order API
    participant DB as PostgreSQL
    participant Outbox as Outbox Publisher
    participant Kafka as Redpanda/Kafka
    participant Worker as Event Consumer
    participant DLQ as Dead-letter Table

API->>DB: Confirm checkout, save order state, and save outbox event in one transaction
Outbox->>DB: Read pending outbox events from order-worker
Outbox->>Kafka: Publish event
    Outbox->>DB: Mark event published
    Worker->>DB: Process published event
    Worker->>DB: Mark event processed
    Worker-->>DLQ: Store exhausted failures with retry metadata
```

Failures are intentionally visible. Publish and consumer failures record retry count, next attempt time, and last error. Exhausted failures are moved to dead-letter records and can be retried through the manual retry API or the operations console.

## Baseline And Improved Modes

Benchmark modes are kept only for evaluation:

- `baseline` correctness mode uses naive repeated-submit and inventory behavior.
- `improved` correctness mode uses strict idempotency and optimistic locking.
- `direct` async mode runs the workflow synchronously.
- `outbox-kafka` async mode uses transactional outbox, event publishing, retry metadata, DLQ, and manual retry.

The default local runtime uses the improved path.

Benchmark report generation uses the recording broker for deterministic CI-friendly evidence. Docker Compose remains the local runtime path for Redpanda/Kafka-backed event publishing.

## API/Worker Runtime Roles

The same backend image supports three runtime roles through `ORDERFLOW_RUNTIME_ROLE`:

- `api`: loads REST and console-facing API components.
- `worker`: loads outbox publisher, Kafka listener, event consumer, and retry schedulers.
- `all`: default compatibility mode for tests and single-process local runs.

Docker Compose runs `order-api` and `order-worker` as separate services. The Kubernetes manifests under `k8s/` deploy the same split with readiness and liveness probes. This is intentionally a lightweight API/worker microservice split, not a full decomposition into separate order, inventory, and payment services.
