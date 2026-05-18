package com.orderflow.dlq;

/**
 * Manual recovery status for a DLQ record.
 */
public enum DeadLetterStatus {
    OPEN,
    REPLAYED
}
