import type { OperationsHealthResponse } from "../api/types";
import { EmptyState, ErrorState, StatusPill } from "../components/StateViews";

interface ServiceHealthPageProps {
  health?: OperationsHealthResponse;
  realtimeConnected: boolean;
  loadError: string;
  onRefresh: () => void;
}

export function ServiceHealthPage({
  health,
  realtimeConnected,
  loadError,
  onRefresh
}: ServiceHealthPageProps) {
  if (loadError !== "" && health === undefined) {
    return (
      <section className="panel full-panel">
        <ErrorState title="Health unavailable" message={loadError} onRetry={onRefresh} />
      </section>
    );
  }

  if (health === undefined) {
    return (
      <section className="panel full-panel">
        <EmptyState title="No health snapshot" body="Refresh once the backend is reachable." />
      </section>
    );
  }

  return (
    <section className="panel full-panel">
      <div className="panel-heading row-heading">
        <div>
          <h2>Service health</h2>
          <p>Backend, database, event mode, retry, and DLQ counters.</p>
        </div>
        <StatusPill tone={realtimeConnected ? "good" : "warn"}>
          {realtimeConnected ? "SSE connected" : "SSE reconnecting"}
        </StatusPill>
      </div>

      <div className="metric-grid">
        <Metric label="Backend" value={health.backendStatus} tone={componentTone(health.backendStatus)} />
        <Metric label="Database" value={health.databaseStatus} tone={componentTone(health.databaseStatus)} />
        <Metric label="Kafka" value={health.kafkaStatus} tone={componentTone(health.kafkaStatus)} />
        <Metric label="Event mode" value={health.eventMode} tone="neutral" />
        <Metric label="Orders" value={String(health.orderCount)} tone="neutral" />
        <Metric label="Inventory SKUs" value={String(health.inventorySkuCount)} tone="neutral" />
        <Metric label="Open DLQ" value={String(health.openDlqCount)} tone={health.openDlqCount > 0 ? "danger" : "good"} />
        <Metric label="Retries" value={String(health.retryCount)} tone={health.retryCount > 0 ? "warn" : "good"} />
      </div>

      <div className="outbox-panel">
        <h3>Outbox counts</h3>
        <div className="outbox-counts">
          {Object.entries(health.outboxCounts).map(([status, count]) => (
            <span key={status}>
              {status}: <b>{count}</b>
            </span>
          ))}
        </div>
      </div>
    </section>
  );
}

interface MetricProps {
  label: string;
  value: string;
  tone: "good" | "warn" | "danger" | "neutral";
}

function Metric({ label, value, tone }: MetricProps) {
  return (
    <div className="metric">
      <span>{label}</span>
      <StatusPill tone={tone}>{value}</StatusPill>
    </div>
  );
}

function componentTone(value: string): MetricProps["tone"] {
  if (value === "UP") {
    return "good";
  }
  if (value === "DOWN") {
    return "danger";
  }
  if (value === "NOT_USED") {
    return "neutral";
  }

  return "warn";
}
