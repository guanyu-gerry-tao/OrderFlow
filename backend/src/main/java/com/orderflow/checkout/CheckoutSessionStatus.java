package com.orderflow.checkout;

/**
 * Lifecycle state for a checkout session.
 */
public enum CheckoutSessionStatus {
    ACTIVE,
    CONFIRMED,
    EXPIRED,
    CANCELLED
}
