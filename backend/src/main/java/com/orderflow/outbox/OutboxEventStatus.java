package com.orderflow.outbox;

/**
 * Processing state for one outbox event.
 */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    PROCESSED,
    DLQ
}
