import { useCallback, useEffect, useMemo, useState } from "react";
import { apiClient } from "./api/client";
import { queryKeys } from "./api/queryKeys";
import type {
  CheckoutSessionResponse,
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
type SectionKey = "orders" | "inventory" | "deadLetters" | "health";
type SectionErrors = Record<SectionKey, string>;

interface RefreshOptions {
  includeHealth?: boolean;
}

const views: Array<{ id: ViewId; label: string }> = [
  { id: "orders", label: "Orders" },
  { id: "timeline", label: "Timeline" },
  { id: "inventory", label: "Inventory" },
  { id: "failed-events", label: "Failed events" },
  { id: "health", label: "Health" }
];
const ACTIVE_CHECKOUT_SESSION_KEY = "orderflow.activeCheckoutSessionId";

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
  const [sectionErrors, setSectionErrors] = useState<SectionErrors>(emptySectionErrors);
  const [createError, setCreateError] = useState("");
  const [seedError, setSeedError] = useState("");
  const [retryError, setRetryError] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [isConfirmingCheckout, setIsConfirmingCheckout] = useState(false);
  const [isSeeding, setIsSeeding] = useState(false);
  const [retryingId, setRetryingId] = useState("");
  const [realtimeConnected, setRealtimeConnected] = useState(false);
  const [activeCheckout, setActiveCheckout] = useState<CheckoutSessionResponse>();

  const selectedOrder = useMemo(
    () => orders.find((order) => order.orderId === selectedOrderId),
    [orders, selectedOrderId]
  );

  const refreshConsole = useCallback(async ({ includeHealth = true }: RefreshOptions = {}) => {
    setLoadError("");
    const nextErrors = emptySectionErrors();
    let loadedSections = 0;
    const [
      ordersResponse,
      inventoryResponse,
      deadLettersResponse,
      healthResponse
    ] = await Promise.allSettled([
      apiClient.listOrders(statusFilter, search),
      apiClient.listInventory(),
      apiClient.listDeadLetters(),
      includeHealth ? apiClient.getHealth() : Promise.resolve(undefined)
    ]);

    if (ordersResponse.status === "fulfilled") {
      loadedSections += 1;
      setOrders(ordersResponse.value);
      if (selectedOrderId === "" && ordersResponse.value.length > 0) {
        setSelectedOrderId(ordersResponse.value[0].orderId);
      }
    } else {
      nextErrors.orders = errorMessage(ordersResponse.reason, "Orders endpoint failed.");
    }

    if (inventoryResponse.status === "fulfilled") {
      loadedSections += 1;
      setInventory(inventoryResponse.value);
    } else {
      nextErrors.inventory = errorMessage(inventoryResponse.reason, "Inventory endpoint failed.");
    }

    if (deadLettersResponse.status === "fulfilled") {
      loadedSections += 1;
      setDeadLetters(deadLettersResponse.value);
    } else {
      nextErrors.deadLetters = errorMessage(deadLettersResponse.reason, "Failed events endpoint failed.");
    }

    if (includeHealth) {
      if (healthResponse.status === "fulfilled") {
        loadedSections += 1;
        setHealth(healthResponse.value);
      } else {
        nextErrors.health = errorMessage(healthResponse.reason, "Health endpoint failed.");
      }
    }

    setSectionErrors(nextErrors);
    if (loadedSections === 0) {
      throw new Error(firstSectionError(nextErrors));
    }
  }, [search, selectedOrderId, statusFilter]);

  const handleRefresh = useCallback(async () => {
    try {
      await refreshConsole();
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Unable to load console data.");
    }
  }, [refreshConsole]);

  const restoreActiveCheckout = useCallback(async () => {
    const checkoutSessionId = localStorage.getItem(ACTIVE_CHECKOUT_SESSION_KEY);
    if (checkoutSessionId === null) {
      return;
    }

    try {
      const checkout = await apiClient.getCheckoutSession(checkoutSessionId);
      if (checkout.status === "ACTIVE") {
        setActiveCheckout(checkout);
        setSelectedOrderId(checkout.order.orderId);
      } else {
        localStorage.removeItem(ACTIVE_CHECKOUT_SESSION_KEY);
        setActiveCheckout(checkout);
      }
    } catch {
      localStorage.removeItem(ACTIVE_CHECKOUT_SESSION_KEY);
      setActiveCheckout(undefined);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      setIsLoading(true);
      try {
        await refreshConsole();
        await restoreActiveCheckout();
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
  }, [refreshConsole, restoreActiveCheckout]);

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
      refreshConsole({ includeHealth: false }).catch(() => setRealtimeConnected(false));
    });

    return () => eventSource.close();
  }, [refreshConsole]);

  async function startCheckout(payload: {
    customerId: string;
    sku: string;
    quantity: number;
  }) {
    setIsCreating(true);
    setCreateError("");
    try {
      const checkout = await apiClient.createCheckoutSession({
        customerId: payload.customerId,
        items: [{ sku: payload.sku, quantity: payload.quantity }]
      });
      localStorage.setItem(ACTIVE_CHECKOUT_SESSION_KEY, checkout.checkoutSessionId);
      setActiveCheckout(checkout);
      setSelectedOrderId(checkout.order.orderId);
      await refreshConsole();
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "Unable to start checkout.");
    } finally {
      setIsCreating(false);
    }
  }

  async function confirmCheckout() {
    if (activeCheckout === undefined) {
      return;
    }

    setIsConfirmingCheckout(true);
    setCreateError("");
    try {
      const checkout = await apiClient.confirmCheckoutSession(activeCheckout.checkoutSessionId, {
        mockPaymentToken: "mock-console-token"
      });
      localStorage.removeItem(ACTIVE_CHECKOUT_SESSION_KEY);
      setActiveCheckout(checkout);
      setSelectedOrderId(checkout.order.orderId);
      setActiveView("timeline");
      await refreshConsole();
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "Unable to confirm checkout.");
    } finally {
      setIsConfirmingCheckout(false);
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
    return <ErrorState title="Backend unavailable" message={loadError} onRetry={handleRefresh} />;
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
          <button type="button" onClick={handleRefresh}>
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
            activeCheckout={activeCheckout}
            loadError={sectionErrors.orders}
            isCreating={isCreating}
            isConfirmingCheckout={isConfirmingCheckout}
            onStatusFilterChange={setStatusFilter}
            onSearchChange={setSearch}
            onSelectOrder={(orderId) => {
              setSelectedOrderId(orderId);
              setActiveView("timeline");
            }}
            onStartCheckout={startCheckout}
            onConfirmCheckout={confirmCheckout}
            onRefresh={handleRefresh}
          />
        ) : null}

        {activeView === "timeline" ? (
          <OrderTimelinePage selectedOrder={selectedOrder} timeline={timeline} />
        ) : null}

        {activeView === "inventory" ? (
          <InventoryDashboardPage
            inventory={inventory}
            seedError={seedError}
            loadError={sectionErrors.inventory}
            isSeeding={isSeeding}
            onSeedInventory={seedInventory}
            onRefresh={handleRefresh}
          />
        ) : null}

        {activeView === "failed-events" ? (
          <FailedEventsPage
            deadLetters={deadLetters}
            retryingId={retryingId}
            retryError={retryError}
            onRetry={retryDeadLetter}
            loadError={sectionErrors.deadLetters}
            onRefresh={handleRefresh}
          />
        ) : null}

        {activeView === "health" ? (
          <ServiceHealthPage
            health={health}
            realtimeConnected={realtimeConnected}
            loadError={sectionErrors.health}
            onRefresh={handleRefresh}
          />
        ) : null}
      </section>
    </main>
  );
}

function emptySectionErrors(): SectionErrors {
  return {
    orders: "",
    inventory: "",
    deadLetters: "",
    health: ""
  };
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

function firstSectionError(errors: SectionErrors): string {
  return Object.values(errors).find((message) => message !== "") ?? "Unable to load console data.";
}
