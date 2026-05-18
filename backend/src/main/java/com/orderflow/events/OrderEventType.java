package com.orderflow.events;

/**
 * Event names used by the order workflow outbox.
 */
public enum OrderEventType {
    ORDER_CREATED,
    INVENTORY_RESERVED
}
