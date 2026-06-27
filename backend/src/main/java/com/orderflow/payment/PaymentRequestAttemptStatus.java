package com.orderflow.payment;

/**
 * Status for one physical payment confirm request.
 */
public enum PaymentRequestAttemptStatus {
    INITIATED,
    AUTHORIZED,
    REPLAYED,
    TIMEOUT,
    FAILED,
    EXPIRED
}
