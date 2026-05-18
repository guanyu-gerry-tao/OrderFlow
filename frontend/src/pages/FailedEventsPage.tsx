import type { DeadLetterEventResponse } from "../api/types";
import { EmptyState, ErrorState, StatusPill } from "../components/StateViews";

interface FailedEventsPageProps {
  deadLetters: DeadLetterEventResponse[];
  retryingId: string;
  retryError: string;
  onRetry: (deadLetterEventId: string) => Promise<void>;
  loadError: string;
  onRefresh: () => void;
}

export function FailedEventsPage({
  deadLetters,
  retryingId,
  retryError,
  onRetry,
  loadError,
  onRefresh
}: FailedEventsPageProps) {
  return (
    <section className="panel full-panel">
      <div className="panel-heading">
        <h2>Failed events</h2>
        <p>DLQ records show exhausted workflow failures that need visible recovery.</p>
      </div>

      {retryError !== "" ? <p className="form-error">{retryError}</p> : null}

      {loadError !== "" ? (
        <ErrorState title="Failed events unavailable" message={loadError} onRetry={onRefresh} />
      ) : deadLetters.length === 0 ? (
        <EmptyState title="No failed events" body="The DLQ is empty for the current backend state." />
      ) : (
        <div className="data-table dlq-table">
          <div className="table-row table-head">
            <span>Event</span>
            <span>Order</span>
            <span>Status</span>
            <span>Error</span>
            <span>Action</span>
          </div>
          {deadLetters.map((event) => (
            <div className="table-row" key={event.id}>
              <span>{event.eventType}</span>
              <span className="mono">{event.aggregateId.slice(0, 8)}</span>
              <StatusPill tone={event.status === "OPEN" ? "danger" : "good"}>{event.status}</StatusPill>
              <span className="truncate" title={event.lastError}>
                {event.lastError}
              </span>
              <button
                type="button"
                disabled={event.status !== "OPEN" || retryingId === event.id}
                onClick={() => onRetry(event.id)}
              >
                {retryingId === event.id ? "Retrying..." : "Retry"}
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
