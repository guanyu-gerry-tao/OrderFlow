# OrderFlow Local Kubernetes

These manifests provide local Kubernetes evidence for the OrderFlow API/worker split. They are intended for Docker Desktop Kubernetes, kind, or minikube, not for production EKS.

## Runtime Shape

- `order-api`: Spring Boot API process for REST checkout, inventory, order timeline, DLQ, operations health, and SSE endpoints.
- `order-worker`: Spring Boot worker process for transactional outbox polling, Kafka/Redpanda consumption, retry, DLQ handling, and async recovery.
- `postgres`, `redis`, and `redpanda`: local runtime dependencies used by both services.

Both `order-api` and `order-worker` use the same backend image with different `ORDERFLOW_RUNTIME_ROLE` values.

## Local Runbook

Build the backend image where your local Kubernetes cluster can see it:

```bash
docker build -f backend/Dockerfile -t orderflow-backend:local .
```

For kind, load the image into the cluster:

```bash
kind load docker-image orderflow-backend:local
```

Apply the manifests:

```bash
kubectl apply -f k8s/
kubectl get pods
```

Port-forward the API:

```bash
kubectl port-forward service/order-api 8080:8080
```

Smoke the API path:

```bash
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/operations/health
```

Check worker ownership through its pod status or logs:

```bash
kubectl get deploy/order-worker
kubectl logs deploy/order-worker
```

For manifest-only validation without a live cluster, run:

```bash
kubectl apply --dry-run=client --validate=false -f k8s/
```

## Boundaries

This is a local deployment mapping for the M7 API/worker split. It does not include autoscaling, production persistent volumes, external secrets, ingress, service mesh, blue/green deploys, or multi-region infrastructure.
