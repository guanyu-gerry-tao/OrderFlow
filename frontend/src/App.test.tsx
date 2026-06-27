import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "./App";

const sampleOrder = {
  orderId: "11111111-1111-1111-1111-111111111111",
  customerId: "customer-console-101",
  status: "COMPLETED",
  items: [{ orderItemId: "item-1", sku: "SKU-M1", quantity: 1 }],
  createdAt: "2026-05-17T10:00:00Z",
  updatedAt: "2026-05-17T10:00:10Z"
};

const checkoutOrder = {
  orderId: "33333333-3333-3333-3333-333333333333",
  customerId: "customer-console-101",
  status: "PENDING_PAYMENT",
  items: [{ orderItemId: "item-checkout", sku: "SKU-M1", quantity: 1 }],
  createdAt: "2026-05-17T10:02:00Z",
  updatedAt: "2026-05-17T10:02:00Z"
};

const confirmedCheckoutOrder = {
  ...checkoutOrder,
  status: "CREATED",
  updatedAt: "2026-05-17T10:03:00Z"
};

const checkoutSession = {
  checkoutSessionId: "44444444-4444-4444-4444-444444444444",
  status: "ACTIVE",
  order: checkoutOrder,
  paymentAttempt: {
    paymentAttemptId: "55555555-5555-5555-5555-555555555555",
    idempotencyKey: "authorize:55555555-5555-5555-5555-555555555555",
    status: "INITIATED",
    expiresAt: "2026-05-17T10:17:00Z"
  },
  requestAttemptId: null,
  requestAttemptStatus: null,
  createdAt: "2026-05-17T10:02:00Z",
  expiresAt: "2026-05-17T10:17:00Z"
};

const sampleHealth = {
  generatedAt: "2026-05-17T10:00:00Z",
  backendStatus: "UP",
  databaseStatus: "UP",
  eventMode: "outbox-kafka",
  eventBroker: "kafka",
  kafkaStatus: "CONFIGURED",
  orderCount: 1,
  inventorySkuCount: 1,
  outboxCounts: { PENDING: 0, PUBLISHED: 0, PROCESSED: 2, DLQ: 0 },
  dlqCount: 1,
  openDlqCount: 1,
  replayedDlqCount: 0,
  retryCount: 2
};

class TestEventSource {
  static instances: TestEventSource[] = [];
  readonly listeners = new Map<string, EventListenerOrEventListenerObject>();

  constructor() {
    TestEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
    this.listeners.set(type, listener);
  }

  emit(type: string, data?: unknown) {
    const listener = this.listeners.get(type);
    const event = new MessageEvent(type, {
      data: data === undefined ? "" : JSON.stringify(data)
    });

    if (typeof listener === "function") {
      listener(event);
      return;
    }

    listener?.handleEvent(event);
  }

  close() {
    return undefined;
  }
}

let failingEndpoint = "";
let deadLetterRequestCount = 0;
let healthRequestCount = 0;
let checkoutCreateRequestCount = 0;
let checkoutGetRequestCount = 0;
let checkoutConfirmRequestCount = 0;
let ordersResponse: unknown[] = [sampleOrder];
let testStorage: Storage;

describe("App", () => {
  beforeEach(() => {
    failingEndpoint = "";
    deadLetterRequestCount = 0;
    healthRequestCount = 0;
    checkoutCreateRequestCount = 0;
    checkoutGetRequestCount = 0;
    checkoutConfirmRequestCount = 0;
    ordersResponse = [sampleOrder];
    testStorage = createTestStorage();
    vi.stubGlobal("localStorage", testStorage);
    vi.stubGlobal("EventSource", TestEventSource);
    vi.stubGlobal("fetch", vi.fn(mockFetch));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    TestEventSource.instances = [];
  });

  it("loads orders and opens the selected timeline", async () => {
    render(<App />);

    expect(await screen.findByText("customer-console-101")).toBeInTheDocument();

    await userEvent.click(screen.getByText("11111111"));

    expect(await screen.findByRole("heading", { name: "Order timeline" })).toBeInTheDocument();
    expect(screen.getByText("Order completed")).toBeInTheDocument();
  });

  it("runs manual retry from the failed events view", async () => {
    render(<App />);

    await screen.findByText("customer-console-101");
    await userEvent.click(screen.getByRole("button", { name: "Failed events" }));
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        "http://localhost:8080/api/dlq/dlq-1/retry",
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  it("keeps available console data visible when one section API fails", async () => {
    failingEndpoint = "dlq";

    render(<App />);

    expect(await screen.findByText("customer-console-101")).toBeInTheDocument();
    expect(screen.queryByText("Backend unavailable")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Failed events" }));

    expect(await screen.findByText("Failed events unavailable")).toBeInTheDocument();
    expect(screen.getByText("DLQ endpoint failed")).toBeInTheDocument();
  });

  it("keeps the global retry view stable when every section remains unavailable", async () => {
    failingEndpoint = "all";

    render(<App />);

    expect(await screen.findByText("Backend unavailable")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(await screen.findByText("Backend unavailable")).toBeInTheDocument();
    expect(screen.getByText("All endpoints failed")).toBeInTheDocument();
  });

  it("starts checkout and restores the same active session after reload", async () => {
    const firstRender = render(<App />);

    await screen.findByText("customer-console-101");
    await userEvent.click(screen.getByRole("button", { name: "Start checkout" }));

    await waitFor(() => {
      expect(screen.getAllByText("PENDING_PAYMENT").length).toBeGreaterThan(0);
    });
    expect(screen.getByText("authorize:55555555-5555-5555-5555-555555555555")).toBeInTheDocument();
    expect(checkoutCreateRequestCount).toBe(1);
    expect(localStorage.getItem("orderflow.activeCheckoutSessionId")).toBe(checkoutSession.checkoutSessionId);

    firstRender.unmount();
    render(<App />);

    expect(await screen.findByText("authorize:55555555-5555-5555-5555-555555555555")).toBeInTheDocument();
    expect(checkoutGetRequestCount).toBeGreaterThanOrEqual(1);
    expect(checkoutCreateRequestCount).toBe(1);
  });

  it("confirms the same payment attempt twice while request attempt ids change", async () => {
    ordersResponse = [checkoutOrder];
    localStorage.setItem("orderflow.activeCheckoutSessionId", checkoutSession.checkoutSessionId);

    render(<App />);

    expect(await screen.findByText("authorize:55555555-5555-5555-5555-555555555555")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment" }));

    expect(await screen.findByRole("heading", { name: "Order timeline" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Orders" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirm payment" }));

    await waitFor(() => {
      expect(checkoutConfirmRequestCount).toBe(2);
    });
    expect(localStorage.getItem("orderflow.activeCheckoutSessionId")).toBeNull();
    await userEvent.click(screen.getByRole("button", { name: "Orders" }));
    expect(screen.getByText("authorize:55555555-5555-5555-5555-555555555555")).toBeInTheDocument();
  });

  it("reflects SSE connection and snapshot events in the health view", async () => {
    render(<App />);

    await screen.findByText("customer-console-101");
    await userEvent.click(screen.getByRole("button", { name: "Health" }));

    const eventSource = TestEventSource.instances[0];
    await act(async () => {
      eventSource.emit("open");
    });

    expect(await screen.findByText("SSE connected")).toBeInTheDocument();

    await act(async () => {
      eventSource.emit("error");
    });

    expect(await screen.findByText("SSE reconnecting")).toBeInTheDocument();

    const deadLetterRequestsBeforeSnapshot = deadLetterRequestCount;
    const healthRequestsBeforeSnapshot = healthRequestCount;
    await act(async () => {
      eventSource.emit("snapshot", {
        generatedAt: "2026-05-17T10:01:00Z",
        health: {
          ...sampleHealth,
          eventMode: "direct"
        }
      });
    });

    await waitFor(() => {
      expect(deadLetterRequestCount).toBeGreaterThan(deadLetterRequestsBeforeSnapshot);
    });
    expect(healthRequestCount).toBe(healthRequestsBeforeSnapshot);
    expect(await screen.findByText("direct mode · health")).toBeInTheDocument();
  });
});

async function mockFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = String(input);
  const method = init?.method ?? "GET";

  if (failingEndpoint === "all" && url.includes("/api/")) {
    return jsonResponse({ message: "All endpoints failed" }, 503);
  }

  if (failingEndpoint === "dlq" && url.includes("/api/dlq")) {
    return jsonResponse({ message: "DLQ endpoint failed" }, 503);
  }

  if (url.includes("/api/orders/11111111-1111-1111-1111-111111111111/timeline")) {
    return jsonResponse({
      events: [
        {
          fromStatus: "PAYMENT_AUTHORIZED",
          toStatus: "COMPLETED",
          message: "Order completed",
          createdAt: "2026-05-17T10:00:10Z",
          sequenceNumber: 4
        }
      ]
    });
  }
  if (url.includes("/api/orders/33333333-3333-3333-3333-333333333333/timeline")) {
    return jsonResponse({
      events: [
        {
          fromStatus: "PENDING_PAYMENT",
          toStatus: "CREATED",
          message: "Payment confirmed",
          createdAt: "2026-05-17T10:03:00Z",
          sequenceNumber: 2
        }
      ]
    });
  }
  if (url.includes("/api/checkout-sessions/44444444-4444-4444-4444-444444444444/confirm")
      && method === "POST") {
    checkoutConfirmRequestCount += 1;
    ordersResponse = [confirmedCheckoutOrder];
    return jsonResponse({
      ...checkoutSession,
      status: "CONFIRMED",
      order: confirmedCheckoutOrder,
      paymentAttempt: {
        ...checkoutSession.paymentAttempt,
        status: "AUTHORIZED"
      },
      requestAttemptId: checkoutConfirmRequestCount === 1
        ? "66666666-6666-6666-6666-666666666666"
        : "77777777-7777-7777-7777-777777777777",
      requestAttemptStatus: checkoutConfirmRequestCount === 1 ? "AUTHORIZED" : "REPLAYED"
    });
  }
  if (url.includes("/api/checkout-sessions/44444444-4444-4444-4444-444444444444")
      && method === "GET") {
    checkoutGetRequestCount += 1;
    return jsonResponse(checkoutSession);
  }
  if (url.includes("/api/checkout-sessions") && method === "POST") {
    checkoutCreateRequestCount += 1;
    ordersResponse = [checkoutOrder];
    return jsonResponse(checkoutSession, 201);
  }
  if (url.includes("/api/orders") && method === "GET") {
    return jsonResponse(ordersResponse);
  }
  if (url.includes("/api/orders") && method === "POST") {
    return jsonResponse(sampleOrder, 201);
  }
  if (url.includes("/api/inventory/seed")) {
    return jsonResponse(undefined, 204);
  }
  if (url.includes("/api/inventory")) {
    return jsonResponse([
      { sku: "SKU-M1", availableQuantity: 9, version: 1, updatedAt: "2026-05-17T10:00:00Z" }
    ]);
  }
  if (url.includes("/api/dlq/dlq-1/retry")) {
    return jsonResponse(undefined, 202);
  }
  if (url.includes("/api/dlq")) {
    deadLetterRequestCount += 1;
    return jsonResponse([
      {
        id: "dlq-1",
        outboxEventId: "outbox-1",
        aggregateId: "11111111-1111-1111-1111-111111111111",
        eventType: "INVENTORY_RESERVED",
        retryCount: 2,
        lastError: "Injected payment timeout",
        status: "OPEN",
        createdAt: "2026-05-17T10:00:00Z",
        replayedAt: null
      }
    ]);
  }
  if (url.includes("/api/operations/health")) {
    healthRequestCount += 1;
    return jsonResponse(sampleHealth);
  }

  return jsonResponse({ message: "Not found" }, 404);
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" }
    })
  );
}

function createTestStorage(): Storage {
  const values = new Map<string, string>();

  return {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(key: string) {
      return values.get(key) ?? null;
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null;
    },
    removeItem(key: string) {
      values.delete(key);
    },
    setItem(key: string, value: string) {
      values.set(key, value);
    }
  };
}
