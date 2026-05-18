export type OrderStatus =
  | "CREATED"
  | "INVENTORY_RESERVED"
  | "PAYMENT_AUTHORIZED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type DeadLetterStatus = "OPEN" | "REPLAYED";

export type OutboxCounts = Record<string, number>;

export interface OrderItemResponse {
  orderItemId: string;
  sku: string;
  quantity: number;
}

export interface OrderResponse {
  orderId: string;
  customerId: string;
  status: OrderStatus;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface TimelineEventResponse {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  message: string;
  createdAt: string;
  sequenceNumber: number;
}

export interface TimelineResponse {
  events: TimelineEventResponse[];
}

export interface InventoryItemResponse {
  sku: string;
  availableQuantity: number;
  version: number;
  updatedAt: string;
}

export interface DeadLetterEventResponse {
  id: string;
  outboxEventId: string;
  aggregateId: string;
  eventType: string;
  retryCount: number;
  lastError: string;
  status: DeadLetterStatus;
  createdAt: string;
  replayedAt: string | null;
}

export interface OperationsHealthResponse {
  generatedAt: string;
  backendStatus: string;
  databaseStatus: string;
  eventMode: string;
  eventBroker: string;
  kafkaStatus: string;
  orderCount: number;
  inventorySkuCount: number;
  outboxCounts: OutboxCounts;
  dlqCount: number;
  openDlqCount: number;
  replayedDlqCount: number;
  retryCount: number;
}

export interface RealtimeSnapshotResponse {
  generatedAt: string;
  health: OperationsHealthResponse;
}

export interface CreateOrderRequest {
  customerId: string;
  items: Array<{
    sku: string;
    quantity: number;
  }>;
}

export interface SeedInventoryRequest {
  sku: string;
  availableQuantity: number;
}
