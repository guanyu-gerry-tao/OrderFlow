import type { FormEvent } from "react";
import type { InventoryItemResponse } from "../api/types";
import { EmptyState } from "../components/StateViews";

interface InventoryDashboardPageProps {
  inventory: InventoryItemResponse[];
  seedError: string;
  isSeeding: boolean;
  onSeedInventory: (payload: { sku: string; availableQuantity: number }) => Promise<void>;
}

export function InventoryDashboardPage({
  inventory,
  seedError,
  isSeeding,
  onSeedInventory
}: InventoryDashboardPageProps) {
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    await onSeedInventory({
      sku: String(formData.get("sku") ?? ""),
      availableQuantity: Number(formData.get("availableQuantity") ?? 0)
    });
  }

  const maxQuantity = Math.max(1, ...inventory.map((item) => item.availableQuantity));

  return (
    <section className="workspace-grid">
      <div className="panel panel-form">
        <div className="panel-heading">
          <h2>Seed inventory</h2>
          <p>Use demo stock to make order workflows repeatable.</p>
        </div>
        <form className="stacked-form" onSubmit={handleSubmit}>
          <label>
            SKU
            <input name="sku" defaultValue="SKU-M1" required />
          </label>
          <label>
            Available quantity
            <input name="availableQuantity" type="number" min="0" defaultValue="10" required />
          </label>
          {seedError !== "" ? <p className="form-error">{seedError}</p> : null}
          <button type="submit" disabled={isSeeding}>
            {isSeeding ? "Saving..." : "Seed inventory"}
          </button>
        </form>
      </div>

      <div className="panel panel-list">
        <div className="panel-heading">
          <h2>Inventory dashboard</h2>
          <p>Available units and optimistic-locking versions.</p>
        </div>
        {inventory.length === 0 ? (
          <EmptyState title="No inventory" body="Seed a SKU before creating orders." />
        ) : (
          <div className="inventory-list">
            {inventory.map((item) => (
              <div className="inventory-row" key={item.sku}>
                <div>
                  <strong>{item.sku}</strong>
                  <span>Version {item.version}</span>
                </div>
                <div className="inventory-meter" aria-label={`${item.sku} available quantity`}>
                  <span style={{ width: `${(item.availableQuantity / maxQuantity) * 100}%` }} />
                </div>
                <b>{item.availableQuantity}</b>
              </div>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
