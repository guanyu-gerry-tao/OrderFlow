package com.orderflow.payment;

/**
 * Result states for simulated payment attempts.
 */
public enum PaymentStatus {
    INITIATED,
    AUTHORIZED,
    FAILED,
    EXPIRED
}
