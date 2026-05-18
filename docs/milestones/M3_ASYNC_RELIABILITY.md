# M3 Async Reliability

## Summary

M3 adds reliable asynchronous event processing for the order workflow. The default runtime now writes workflow events into a transactional outbox, publishes them to a Kafka-compatible broker, processes them through consumers, records retry metadata when failures happen, moves exhausted failures into a dead-letter table, and exposes a manual retry API.

## Implemented Scope

- `outbox_events` table for transactional workflow events.
- `dead_letter_events` table for exhausted event-processing failures.
- `ORDERFLOW_EVENT_MODE=outbox-kafka` default runtime mode.
- `ORDERFLOW_EVENT_MODE=direct` direct path for tests and comparison profiles.
- Redpanda service in Docker Compose as the local Kafka-compatible broker.
- Scheduled outbox publisher that sends due events to the configured broker.
- Kafka event broker and listener for the default local runtime.
- Recording event broker for deterministic integration tests.
- Consumer processing for:
  - `ORDER_CREATED` -> inventory reservation.
  - `INVENTORY_RESERVED` -> simulated payment authorization and order completion.
- Retry metadata on outbox events: retry count, next attempt time, and last error.
- DLQ routing after retry exhaustion.
- `GET /api/dlq` to inspect dead-letter records.
- `POST /api/dlq/{deadLetterEventId}/retry` to requeue and replay a dead-letter event.
- Failure injection coverage in tests for publish failure, consumer crash, and payment timeout.

## Configuration

Default runtime settings:

```text
ORDERFLOW_EVENT_MODE=outbox-kafka
ORDERFLOW_EVENT_BROKER=kafka
ORDERFLOW_EVENT_TOPIC=orderflow.order-events
ORDERFLOW_EVENT_RETRY_MAX_ATTEMPTS=3
ORDERFLOW_EVENT_RETRY_INITIAL_BACKOFF=5s
```

Direct comparison/test mode:

```text
ORDERFLOW_EVENT_MODE=direct
ORDERFLOW_EVENT_BROKER=recording
```

## API Behavior

In `outbox-kafka` mode, `POST /api/orders` creates the order and writes the first outbox event in the same database transaction. The response can show the order in `CREATED` while the asynchronous consumer continues the workflow.

DLQ records can be listed and manually replayed:

```bash
curl http://localhost:8080/api/dlq

curl -X POST http://localhost:8080/api/dlq/<dead-letter-event-id>/retry
```

Manual retry writes an audit-log entry before replaying the event so the recovery step remains visible in the order timeline.

## Validation

Run the backend test suite:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew test --no-daemon
```

Run only the M3 tests:

```bash
DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./gradlew :backend:test \
  --tests com.orderflow.order.OutboxIntegrationTest \
  --tests com.orderflow.order.RetryDlqIntegrationTest \
  --no-daemon
```

Validate the Docker Compose file:

```bash
docker compose config
```

## Current Limitations

- The frontend operations console is not part of M3.
- The async reliability benchmark is not implemented in M3.
- Saga compensation for payment failure or cancellation is left for a later milestone.
- M3 keeps modular service boundaries in one backend process; it does not split order, inventory, and payment into separate deployable services.
