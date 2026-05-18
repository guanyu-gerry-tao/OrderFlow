import type { FormEvent } from "react";
import type { OrderResponse, OrderStatus } from "../api/types";
import { EmptyState, StatusPill } from "../components/StateViews";

interface OrdersPageProps {
  orders: OrderResponse[];
  selectedOrderId: string;
  statusFilter: OrderStatus | "";
  search: string;
  createError: string;
  isCreating: boolean;
  onStatusFilterChange: (status: OrderStatus | "") => void;
  onSearchChange: (search: string) => void;
  onSelectOrder: (orderId: string) => void;
  onCreateOrder: (payload: {
    customerId: string;
    sku: string;
    quantity: number;
    idempotencyKey: string;
  }) => Promise<void>;
}

const orderStatuses: Array<OrderStatus | ""> = [
  "",
  "CREATED",
  "INVENTORY_RESERVED",
  "PAYMENT_AUTHORIZED",
  "COMPLETED",
  "FAILED",
  "CANCELLED"
];

export function OrdersPage({
  orders,
  selectedOrderId,
  statusFilter,
  search,
  createError,
  isCreating,
  onStatusFilterChange,
  onSearchChange,
  onSelectOrder,
  onCreateOrder
}: OrdersPageProps) {
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    await onCreateOrder({
      customerId: String(formData.get("customerId") ?? ""),
      sku: String(formData.get("sku") ?? ""),
      quantity: Number(formData.get("quantity") ?? 1),
      idempotencyKey: String(formData.get("idempotencyKey") ?? "")
    });
  }

  return (
    <section className="workspace-grid">
      <div className="panel panel-form">
        <div className="panel-heading">
          <h2>Create order</h2>
          <p>Seed inventory first, then create a small workflow order.</p>
        </div>
        <form className="stacked-form" onSubmit={handleSubmit}>
          <label>
            Customer ID
            <input name="customerId" defaultValue="customer-console-101" required />
          </label>
          <label>
            SKU
            <input name="sku" defaultValue="SKU-M1" required />
          </label>
          <label>
            Quantity
            <input name="quantity" type="number" min="1" defaultValue="1" required />
          </label>
          <label>
            Idempotency key
            <input name="idempotencyKey" defaultValue={`console-${Date.now()}`} required />
          </label>
          {createError !== "" ? <p className="form-error">{createError}</p> : null}
          <button type="submit" disabled={isCreating}>
            {isCreating ? "Creating..." : "Create order"}
          </button>
        </form>
      </div>

      <div className="panel panel-list">
        <div className="panel-heading row-heading">
          <div>
            <h2>Orders</h2>
            <p>Filter by state, customer, SKU, or order id.</p>
          </div>
          <div className="inline-controls">
            <select
              aria-label="Status filter"
              value={statusFilter}
              onChange={(event) => onStatusFilterChange(event.target.value as OrderStatus | "")}
            >
              {orderStatuses.map((status) => (
                <option key={status || "ALL"} value={status}>
                  {status || "ALL"}
                </option>
              ))}
            </select>
            <input
              aria-label="Search orders"
              value={search}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="Search"
            />
          </div>
        </div>

        {orders.length === 0 ? (
          <EmptyState title="No orders found" body="Create an order or clear the filters." />
        ) : (
          <div className="data-table">
            <div className="table-row table-head">
              <span>Order</span>
              <span>Customer</span>
              <span>Status</span>
              <span>Items</span>
            </div>
            {orders.map((order) => (
              <button
                className={`table-row table-button ${selectedOrderId === order.orderId ? "selected" : ""}`}
                key={order.orderId}
                type="button"
                onClick={() => onSelectOrder(order.orderId)}
              >
                <span className="mono">{shortId(order.orderId)}</span>
                <span>{order.customerId}</span>
                <StatusPill tone={order.status === "COMPLETED" ? "good" : "neutral"}>
                  {order.status}
                </StatusPill>
                <span>{order.items.map((item) => `${item.sku} x${item.quantity}`).join(", ")}</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function shortId(orderId: string): string {
  return orderId.slice(0, 8);
}
