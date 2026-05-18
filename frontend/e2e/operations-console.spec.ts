import { expect, test } from "@playwright/test";

const order = {
  orderId: "22222222-2222-2222-2222-222222222222",
  customerId: "customer-e2e",
  status: "COMPLETED",
  items: [{ orderItemId: "item-2", sku: "SKU-E2E", quantity: 1 }],
  createdAt: "2026-05-17T11:00:00Z",
  updatedAt: "2026-05-17T11:00:10Z"
};

test.beforeEach(async ({ page }) => {
  await page.route("http://localhost:8080/api/**", async (route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/orders/22222222-2222-2222-2222-222222222222/timeline")) {
      await route.fulfill({
        json: {
          events: [
            {
              fromStatus: "PAYMENT_AUTHORIZED",
              toStatus: "COMPLETED",
              message: "Order completed",
              createdAt: "2026-05-17T11:00:10Z",
              sequenceNumber: 4
            }
          ]
        }
      });
      return;
    }
    if (url.includes("/orders") && method === "POST") {
      await route.fulfill({ status: 201, json: order });
      return;
    }
    if (url.includes("/orders")) {
      await route.fulfill({ json: [order] });
      return;
    }
    if (url.includes("/inventory/seed")) {
      await route.fulfill({ status: 204, body: "" });
      return;
    }
    if (url.includes("/inventory")) {
      await route.fulfill({
        json: [{ sku: "SKU-E2E", availableQuantity: 8, version: 2, updatedAt: "2026-05-17T11:00:00Z" }]
      });
      return;
    }
    if (url.includes("/dlq/dlq-e2e/retry")) {
      await route.fulfill({ status: 202, body: "" });
      return;
    }
    if (url.includes("/dlq")) {
      await route.fulfill({
        json: [
          {
            id: "dlq-e2e",
            outboxEventId: "outbox-e2e",
            aggregateId: "22222222-2222-2222-2222-222222222222",
            eventType: "INVENTORY_RESERVED",
            retryCount: 2,
            lastError: "Injected payment timeout",
            status: "OPEN",
            createdAt: "2026-05-17T11:00:00Z",
            replayedAt: null
          }
        ]
      });
      return;
    }
    if (url.includes("/operations/health")) {
      await route.fulfill({
        json: {
          generatedAt: "2026-05-17T11:00:00Z",
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
        }
      });
      return;
    }

    await route.fulfill({ status: 404, json: { message: "Not found" } });
  });
});

test("creates an order and shows its timeline", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("customer-e2e")).toBeVisible();
  await page.getByRole("button", { name: "Create order" }).click();

  await expect(page.getByRole("heading", { name: "Order timeline" })).toBeVisible();
  await expect(page.getByText("Order completed")).toBeVisible();
});

test("views failed events and triggers manual retry", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Failed events" }).click();
  await expect(page.getByText("Injected payment timeout")).toBeVisible();
  await page.getByRole("button", { name: "Retry" }).click();

  await expect(page.getByRole("button", { name: "Retrying..." })).toBeVisible();
});
