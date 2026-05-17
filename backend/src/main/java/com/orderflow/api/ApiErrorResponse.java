package com.orderflow.api;

/**
 * Standard response body for API errors.
 *
 * @param code stable error category
 * @param message human-readable error message
 */
public record ApiErrorResponse(
        String code,
        String message
) {
}
