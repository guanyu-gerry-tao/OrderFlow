import { useCallback, useEffect, useMemo, useState } from "react";
import { apiClient } from "./api/client";
import { queryKeys } from "./api/queryKeys";
import type {
  DeadLetterEventResponse,
  InventoryItemResponse,
  OperationsHealthResponse,
  OrderResponse,
  OrderStatus,
  TimelineResponse
} from "./api/types";
import { ErrorState, LoadingState } from "./components/StateViews";
import { FailedEventsPage } from "./pages/FailedEventsPage";
import { InventoryDashboardPage } from "./pages/InventoryDashboardPage";
import { OrdersPage } from "./pages/OrdersPage";
import { OrderTimelinePage } from "./pages/OrderTimelinePage";
import { ServiceHealthPage } from "./pages/ServiceHealthPage";

type ViewId = "orders" | "timeline" | "inventory" | "failed-events" | "health";

const views: Array<{ id: ViewId; label: string }> = [
  { id: "orders", label: "Orders" },
  { id: "timeline", label: "Timeline" },
  { id: "inventory", label: "Inventory" },
  { id: "failed-events", label: "Failed events" },
  { id: "health", label: "Health" }
];

export function App() {
  const [activeView, setActiveView] = useState<ViewId>("orders");
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [inventory, setInventory] = useState<InventoryItemResponse[]>([]);
  const [deadLetters, setDeadLetters] = useState<DeadLetterEventResponse[]>([]);
  const [health, setHealth] = useState<OperationsHealthResponse>();
  const [timeline, setTimeline] = useState<TimelineResponse>();
  const [selectedOrderId, setSelectedOrderId] = useState("");
  const [statusFilter, setStatusFilter] = useState<OrderStatus | "">("");
  const [search, setSearch] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [createError, setCreateError] = useState("");
  const [seedError, setSeedError] = useState("");
  const [retryError, setRetryError] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [isSeeding, setIsSeeding] = useState(false);
  const [retryingId, setRetryingId] = useState("");
  const [realtimeConnected, setRealtimeConnected] = useState(false);

  const selectedOrder = useMemo(
    () => orders.find((order) => order.orderId === selectedOrderId),
    [orders, selectedOrderId]
  );

  const refreshConsole = useCallback(async () => {
    setLoadError("");
    const [ordersResponse, inventoryResponse, deadLettersResponse, healthResponse] = await Promise.all([
      apiClient.listOrders(statusFilter, search),
      apiClient.listInventory(),
      apiClient.listDeadLetters(),
      apiClient.getHealth()
    ]);

    setOrders(ordersResponse);
    setInventory(inventoryResponse);
    setDeadLetters(deadLettersResponse);
    setHealth(healthResponse);
    if (selectedOrderId === "" && ordersResponse.length > 0) {
      setSelectedOrderId(ordersResponse[0].orderId);
    }
  }, [search, selectedOrderId, statusFilter]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      setIsLoading(true);
      try {
        await refreshConsole();
      } catch (error) {
        if (isMounted) {
          setLoadError(error instanceof Error ? error.message : "Unable to load console data.");
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    loadData();
    return () => {
      isMounted = false;
    };
  }, [refreshConsole]);

  useEffect(() => {
    if (selectedOrderId === "") {
      setTimeline(undefined);
      return;
    }

    apiClient.getTimeline(selectedOrderId)
      .then(setTimeline)
      .catch(() => setTimeline(undefined));
  }, [selectedOrderId, orders]);

  useEffect(() => {
    if (import.meta.env.VITE_DISABLE_SSE === "true") {
      return;
    }

    const eventSource = new EventSource(apiClient.realtimeUrl());

    eventSource.addEventListener("open", () => setRealtimeConnected(true));
    eventSource.addEventListener("error", () => setRealtimeConnected(false));
    eventSource.addEventListener("snapshot", (event) => {
      const snapshot = apiClient.parseRealtimeSnapshot(event as MessageEvent<string>);
      setHealth(snapshot.health);
      refreshConsole().catch(() => setRealtimeConnected(false));
    });

    return () => eventSource.close();
  }, [refreshConsole]);

  async function createOrder(payload: {
    customerId: string;
    sku: string;
    quantity: number;
    idempotencyKey: string;
  }) {
    setIsCreating(true);
    setCreateError("");
    try {
      const order = await apiClient.createOrder(
        {
          customerId: payload.customerId,
          items: [{ sku: payload.sku, quantity: payload.quantity }]
        },
        payload.idempotencyKey
      );
      setSelectedOrderId(order.orderId);
      setActiveView("timeline");
      await refreshConsole();
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "Unable to create order.");
    } finally {
      setIsCreating(false);
    }
  }

  async function seedInventory(payload: { sku: string; availableQuantity: number }) {
    setIsSeeding(true);
    setSeedError("");
    try {
      await apiClient.seedInventory(payload);
      await refreshConsole();
    } catch (error) {
      setSeedError(error instanceof Error ? error.message : "Unable to seed inventory.");
    } finally {
      setIsSeeding(false);
    }
  }

  async function retryDeadLetter(deadLetterEventId: string) {
    setRetryingId(deadLetterEventId);
    setRetryError("");
    try {
      await apiClient.retryDeadLetter(deadLetterEventId);
      await refreshConsole();
    } catch (error) {
      setRetryError(error instanceof Error ? error.message : "Manual retry failed.");
    } finally {
      setRetryingId("");
    }
  }

  if (isLoading) {
    return <LoadingState label="Loading operations console..." />;
  }

  if (loadError !== "") {
    return <ErrorState title="Backend unavailable" message={loadError} onRetry={refreshConsole} />;
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand-block">
          <span className="brand-mark">OF</span>
          <div>
            <h1>OrderFlow</h1>
            <p>Operations Console</p>
          </div>
        </div>
        <nav aria-label="Console views">
          {views.map((view) => (
            <button
              key={view.id}
              type="button"
              className={activeView === view.id ? "active" : ""}
              onClick={() => setActiveView(view.id)}
            >
              {view.label}
            </button>
          ))}
        </nav>
      </aside>

      <section className="main-surface">
        <header className="topbar">
          <div>
            <h2>{views.find((view) => view.id === activeView)?.label}</h2>
            <p>
              {health?.eventMode ?? "outbox-kafka"} mode · {queryKeys.health}
            </p>
          </div>
          <button type="button" onClick={refreshConsole}>
            Refresh
          </button>
        </header>

        {activeView === "orders" ? (
          <OrdersPage
            orders={orders}
            selectedOrderId={selectedOrderId}
            statusFilter={statusFilter}
            search={search}
            createError={createError}
            isCreating={isCreating}
            onStatusFilterChange={setStatusFilter}
            onSearchChange={setSearch}
            onSelectOrder={(orderId) => {
              setSelectedOrderId(orderId);
              setActiveView("timeline");
            }}
            onCreateOrder={createOrder}
          />
        ) : null}

        {activeView === "timeline" ? (
          <OrderTimelinePage selectedOrder={selectedOrder} timeline={timeline} />
        ) : null}

        {activeView === "inventory" ? (
          <InventoryDashboardPage
            inventory={inventory}
            seedError={seedError}
            isSeeding={isSeeding}
            onSeedInventory={seedInventory}
          />
        ) : null}

        {activeView === "failed-events" ? (
          <FailedEventsPage
            deadLetters={deadLetters}
            retryingId={retryingId}
            retryError={retryError}
            onRetry={retryDeadLetter}
          />
        ) : null}

        {activeView === "health" ? (
          <ServiceHealthPage health={health} realtimeConnected={realtimeConnected} />
        ) : null}
      </section>
    </main>
  );
}
