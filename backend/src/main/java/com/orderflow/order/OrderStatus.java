package com.orderflow.order;

/**
 * Explicit states for the order workflow.
 */
public enum OrderStatus {
    PENDING_PAYMENT,
    CREATED,
    INVENTORY_RESERVED,
    PAYMENT_AUTHORIZED,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED
}
