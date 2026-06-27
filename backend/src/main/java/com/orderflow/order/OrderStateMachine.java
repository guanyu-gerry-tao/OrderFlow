package com.orderflow.order;

import java.util.Map;
import java.util.Set;

/**
 * Centralizes valid order workflow state transitions.
 */
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.CREATED, OrderStatus.EXPIRED, OrderStatus.CANCELLED),
            OrderStatus.CREATED, Set.of(OrderStatus.INVENTORY_RESERVED, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.INVENTORY_RESERVED, Set.of(OrderStatus.PAYMENT_AUTHORIZED, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_AUTHORIZED, Set.of(OrderStatus.COMPLETED, OrderStatus.FAILED, OrderStatus.CANCELLED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.FAILED, Set.of(),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.EXPIRED, Set.of()
    );

    /**
     * Validates that a status transition is allowed.
     *
     * @param currentStatus current order status
     * @param nextStatus requested next status
     */
    public void validateTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        Set<OrderStatus> nextStatuses = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());

        if (!nextStatuses.contains(nextStatus)) {
            throw new IllegalStateException("Invalid order status transition from " + currentStatus + " to " + nextStatus);
        }
    }
}
