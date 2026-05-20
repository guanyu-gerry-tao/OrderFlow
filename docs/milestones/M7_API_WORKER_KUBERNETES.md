# M7 API/Worker Kubernetes Evidence

M7 splits the OrderFlow backend runtime into two deployable service roles while keeping the core business logic in one Spring Boot codebase.

## What Changed

- `order-api` handles REST APIs, operations-console reads, manual retry requests, and SSE health snapshots.
- `order-worker` handles transactional outbox polling, Kafka/Redpanda publishing and consumption, retry scheduling, DLQ recovery, and asynchronous workflow completion.
- Docker Compose now starts both backend roles as separate services.
- Kubernetes manifests under `k8s/` define local deployments for `order-api`, `order-worker`, PostgreSQL, Redis, and Redpanda.
- Both backend roles expose Spring Boot actuator readiness and liveness probes.

## Runtime Roles

The backend image uses `ORDERFLOW_RUNTIME_ROLE`:

| Role | Purpose |
| --- | --- |
| `api` | REST, console APIs, SSE snapshots |
| `worker` | Outbox publisher, Kafka listener, event consumer, retry scheduler |
| `all` | Backward-compatible single-process default for tests and local experiments |

## Validation

Run lightweight manifest validation:

```bash
./scripts/smoke/run-api-worker-smoke.sh manifest
```

Run the full Docker Compose smoke when Docker is available:

```bash
./scripts/smoke/run-api-worker-smoke.sh compose
```

The compose smoke starts dependencies, waits for API and worker readiness, seeds inventory, creates one order through `order-api`, and waits until `order-worker` completes the asynchronous workflow.

Run local Kubernetes validation when kind, Docker Desktop Kubernetes, or minikube is available:

```bash
docker build -f backend/Dockerfile -t orderflow-backend:local .
kind create cluster --name orderflow-m7
kind load docker-image orderflow-backend:local --name orderflow-m7
kubectl apply -f k8s/
kubectl wait --for=condition=ready pod -l app=order-api --timeout=240s
kubectl wait --for=condition=ready pod -l app=order-worker --timeout=240s
kubectl port-forward service/order-api 18080:8080
```

Then call the API through the port-forward, create an order, and confirm the worker completes it asynchronously. This flow was validated locally with kind for the M7 branch.

For full local Kubernetes run steps, see `k8s/README.md`.

## Boundary

This milestone provides a lightweight API/worker service split and local Kubernetes deployment evidence. It does not introduce production EKS, service mesh, autoscaling tuning, multi-region infrastructure, or separate order/inventory/payment services.
