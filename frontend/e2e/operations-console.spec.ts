import { expect, test } from "@playwright/test";

const order = {
  orderId: "22222222-2222-2222-2222-222222222222",
  customerId: "customer-e2e",
  status: "COMPLETED",
  items: [{ orderItemId: "item-2", sku: "SKU-E2E", quantity: 1 }],
  createdAt: "2026-05-17T11:00:00Z",
  updatedAt: "2026-05-17T11:00:10Z"
};

const checkoutOrder = {
  orderId: "33333333-3333-3333-3333-333333333333",
  customerId: "customer-e2e",
  status: "PENDING_PAYMENT",
  items: [{ orderItemId: "item-checkout-e2e", sku: "SKU-E2E", quantity: 1 }],
  createdAt: "2026-05-17T11:02:00Z",
  updatedAt: "2026-05-17T11:02:00Z"
};

const confirmedCheckoutOrder = {
  ...checkoutOrder,
  status: "CREATED",
  updatedAt: "2026-05-17T11:03:00Z"
};

const checkoutSession = {
  checkoutSessionId: "44444444-4444-4444-4444-444444444444",
  status: "ACTIVE",
  order: checkoutOrder,
  paymentAttempt: {
    paymentAttemptId: "55555555-5555-5555-5555-555555555555",
    idempotencyKey: "authorize:55555555-5555-5555-5555-555555555555",
    status: "INITIATED",
    expiresAt: "2026-05-17T11:17:00Z"
  },
  requestAttemptId: null,
  requestAttemptStatus: null,
  createdAt: "2026-05-17T11:02:00Z",
  expiresAt: "2026-05-17T11:17:00Z"
};

test.beforeEach(async ({ page }) => {
  let orders = [order];

  await page.route("http://localhost:8080/api/**", async (route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.includes("/orders/33333333-3333-3333-3333-333333333333/timeline")) {
      await route.fulfill({
        json: {
          events: [
            {
              fromStatus: "PENDING_PAYMENT",
              toStatus: "CREATED",
              message: "Payment confirmed",
              createdAt: "2026-05-17T11:03:00Z",
              sequenceNumber: 2
            }
          ]
        }
      });
      return;
    }
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
    if (url.includes("/checkout-sessions/44444444-4444-4444-4444-444444444444/confirm")
        && method === "POST") {
      orders = [confirmedCheckoutOrder];
      await route.fulfill({
        json: {
          ...checkoutSession,
          status: "CONFIRMED",
          order: confirmedCheckoutOrder,
          paymentAttempt: {
            ...checkoutSession.paymentAttempt,
            status: "AUTHORIZED"
          },
          requestAttemptId: "66666666-6666-6666-6666-666666666666",
          requestAttemptStatus: "AUTHORIZED"
        }
      });
      return;
    }
    if (url.includes("/checkout-sessions/44444444-4444-4444-4444-444444444444")
        && method === "GET") {
      await route.fulfill({ json: checkoutSession });
      return;
    }
    if (url.includes("/checkout-sessions") && method === "POST") {
      orders = [checkoutOrder];
      await route.fulfill({ status: 201, json: checkoutSession });
      return;
    }
    if (url.includes("/orders")) {
      await route.fulfill({ json: orders });
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

test("starts checkout, survives reload, and confirms payment", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("customer-e2e")).toBeVisible();
  await page.getByRole("button", { name: "Start checkout" }).click();
  await expect(page.getByText("Order 33333333 · PENDING_PAYMENT")).toBeVisible();
  await expect(page.getByText("authorize:55555555-5555-5555-5555-555555555555")).toBeVisible();

  await page.reload();

  await expect(page.getByText("authorize:55555555-5555-5555-5555-555555555555")).toBeVisible();
  await page.getByRole("button", { name: "Confirm payment" }).click();

  await expect(page.getByRole("heading", { name: "Order timeline" })).toBeVisible();
  await expect(page.getByText("Payment confirmed")).toBeVisible();
});

test("views failed events and triggers manual retry", async ({ page }) => {
  await page.goto("/");

  await page.getByRole("button", { name: "Failed events" }).click();
  await expect(page.getByText("Injected payment timeout")).toBeVisible();

  const retryRequest = page.waitForRequest((request) =>
    request.url().includes("/dlq/dlq-e2e/retry") && request.method() === "POST"
  );
  await page.getByRole("button", { name: "Retry" }).click();

  await retryRequest;
  await expect(page.getByRole("button", { name: "Retry" })).toBeVisible();
});
