import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ServiceHealthPage } from "./ServiceHealthPage";

const degradedHealth = {
  generatedAt: "2026-05-17T10:00:00Z",
  backendStatus: "DEGRADED",
  databaseStatus: "DOWN",
  eventMode: "outbox-kafka",
  eventBroker: "kafka",
  kafkaStatus: "CONFIGURED",
  orderCount: 0,
  inventorySkuCount: 0,
  outboxCounts: { PENDING: 0, PUBLISHED: 0, PROCESSED: 0, DLQ: 0 },
  dlqCount: 0,
  openDlqCount: 0,
  replayedDlqCount: 0,
  retryCount: 0
};

describe("ServiceHealthPage", () => {
  it("does not show degraded backend or down database states as healthy", () => {
    render(
      <ServiceHealthPage
        health={degradedHealth}
        realtimeConnected={false}
        loadError=""
        onRefresh={() => undefined}
      />
    );

    expect(screen.getByText("DEGRADED")).toHaveClass("status-warn");
    expect(screen.getByText("DOWN")).toHaveClass("status-danger");
    expect(screen.getByText("CONFIGURED")).toHaveClass("status-warn");
  });
});
