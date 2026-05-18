import type { OrderResponse, TimelineResponse } from "../api/types";
import { EmptyState, StatusPill } from "../components/StateViews";

interface OrderTimelinePageProps {
  selectedOrder?: OrderResponse;
  timeline?: TimelineResponse;
}

export function OrderTimelinePage({ selectedOrder, timeline }: OrderTimelinePageProps) {
  if (selectedOrder === undefined) {
    return (
      <section className="panel full-panel">
        <EmptyState title="No order selected" body="Select an order to inspect its workflow timeline." />
      </section>
    );
  }

  return (
    <section className="panel full-panel">
      <div className="panel-heading row-heading">
        <div>
          <h2>Order timeline</h2>
          <p className="mono">{selectedOrder.orderId}</p>
        </div>
        <StatusPill tone={selectedOrder.status === "COMPLETED" ? "good" : "neutral"}>
          {selectedOrder.status}
        </StatusPill>
      </div>

      {timeline === undefined || timeline.events.length === 0 ? (
        <EmptyState title="Timeline is empty" body="The backend has not recorded audit events yet." />
      ) : (
        <ol className="timeline">
          {timeline.events.map((event) => (
            <li key={`${event.sequenceNumber}-${event.createdAt}`}>
              <div className="timeline-marker">{event.sequenceNumber}</div>
              <div>
                <strong>{event.message}</strong>
                <span>
                  {event.fromStatus ?? "START"} {"->"} {event.toStatus}
                </span>
                <time>{formatTime(event.createdAt)}</time>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("en", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(value));
}
