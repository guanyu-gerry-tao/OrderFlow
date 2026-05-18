import type {
  CreateOrderRequest,
  DeadLetterEventResponse,
  InventoryItemResponse,
  OperationsHealthResponse,
  OrderResponse,
  OrderStatus,
  RealtimeSnapshotResponse,
  SeedInventoryRequest,
  TimelineResponse
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

export class ApiClientError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers
    }
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new ApiClientError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (typeof body.message === "string") {
      return body.message;
    }
  } catch {
    return `Request failed with status ${response.status}`;
  }

  return `Request failed with status ${response.status}`;
}

export const apiClient = {
  listOrders(status: OrderStatus | "", search: string): Promise<OrderResponse[]> {
    const params = new URLSearchParams();
    if (status !== "") {
      params.set("status", status);
    }
    if (search.trim() !== "") {
      params.set("search", search.trim());
    }

    const suffix = params.toString() === "" ? "" : `?${params.toString()}`;
    return request<OrderResponse[]>(`/orders${suffix}`);
  },

  createOrder(payload: CreateOrderRequest, idempotencyKey: string): Promise<OrderResponse> {
    return request<OrderResponse>("/orders", {
      method: "POST",
      body: JSON.stringify(payload),
      headers: {
        "Idempotency-Key": idempotencyKey
      }
    });
  },

  getTimeline(orderId: string): Promise<TimelineResponse> {
    return request<TimelineResponse>(`/orders/${orderId}/timeline`);
  },

  listInventory(): Promise<InventoryItemResponse[]> {
    return request<InventoryItemResponse[]>("/inventory");
  },

  seedInventory(payload: SeedInventoryRequest): Promise<void> {
    return request<void>("/inventory/seed", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  },

  listDeadLetters(): Promise<DeadLetterEventResponse[]> {
    return request<DeadLetterEventResponse[]>("/dlq");
  },

  retryDeadLetter(deadLetterEventId: string): Promise<void> {
    return request<void>(`/dlq/${deadLetterEventId}/retry`, {
      method: "POST"
    });
  },

  getHealth(): Promise<OperationsHealthResponse> {
    return request<OperationsHealthResponse>("/operations/health");
  },

  realtimeUrl(): string {
    return `${API_BASE_URL}/realtime/events`;
  },

  parseRealtimeSnapshot(event: MessageEvent<string>): RealtimeSnapshotResponse {
    return JSON.parse(event.data) as RealtimeSnapshotResponse;
  }
};
