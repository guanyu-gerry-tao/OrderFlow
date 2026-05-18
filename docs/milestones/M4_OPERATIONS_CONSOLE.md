# M4 Operations Console

## Summary

M4 adds a React TypeScript operations console for workflow visibility and recovery. The console can create orders, inspect order timelines, view inventory, inspect failed events, trigger manual retry, and check service health. The backend now provides the read APIs and Server-Sent Events stream needed by the console.

## Implemented Scope

- `frontend/` Vite React TypeScript app.
- Typed API client layer under `frontend/src/api/`.
- Orders view with status filtering, text search, order selection, and order creation.
- Order timeline view backed by audit-log timeline data.
- Inventory dashboard with seed-inventory form and available quantity display.
- Failed events view backed by the DLQ API and manual retry endpoint.
- Service health view with backend, database, event mode, Kafka configuration, outbox, retry, and DLQ counters.
- Page-level `ErrorBoundary`.
- Loading, empty, error, and retry UI states.
- Backend CORS support for the local console.
- Backend order list, inventory list, operations health, and SSE snapshot APIs.
- Docker Compose frontend service.

## API Additions

```text
GET /api/orders?status=<status>&search=<text>
GET /api/inventory
GET /api/operations/health
GET /api/realtime/events
```

Existing APIs are used by the console:

```text
POST /api/orders
GET /api/orders/{orderId}/timeline
POST /api/inventory/seed
GET /api/dlq
POST /api/dlq/{deadLetterEventId}/retry
```

## Local Commands

Install frontend dependencies:

```bash
cd frontend
npm install
```

Run frontend tests and build:

```bash
cd frontend
npm test
npm run build
npm run e2e
```

Run the full local stack:

```bash
docker compose up --build
```

The console is served on `http://localhost:5173` when using Docker Compose or `npm run dev`.

## Validation

The M4 implementation was validated with:

```bash
cd frontend && npm test
cd frontend && npm run build
cd frontend && npm run e2e
./gradlew test --no-daemon
docker compose config
```

The Playwright test server uses port `5178` to avoid reusing another local app on `5173`.

## Current Limitations

- The console is intentionally local-development oriented and has no login, authorization, or multi-tenant controls.
- The SSE stream sends lightweight health snapshots. It does not yet stream every individual audit-log event as a separate event envelope.
- Frontend CI is not wired into GitHub Actions yet; expanded CI is planned for the evidence-package milestone.
- Async reliability benchmark automation is not part of M4.
