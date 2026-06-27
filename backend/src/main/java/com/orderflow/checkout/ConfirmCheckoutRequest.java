package com.orderflow.checkout;

/**
 * Request body for confirming the active checkout payment attempt.
 *
 * @param mockPaymentToken simulated provider token
 */
public record ConfirmCheckoutRequest(String mockPaymentToken) {
}
