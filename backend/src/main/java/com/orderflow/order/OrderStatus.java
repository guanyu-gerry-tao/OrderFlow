package com.orderflow.order;

/**
 * Explicit states for the order workflow.
 */
public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVED,
    PAYMENT_AUTHORIZED,
    COMPLETED,
    FAILED,
    CANCELLED
}
