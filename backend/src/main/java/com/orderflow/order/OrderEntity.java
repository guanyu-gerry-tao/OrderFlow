package com.orderflow.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Stores the core order record and its current workflow status.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
    }

    /**
     * Creates a new order in CREATED status.
     *
     * @param customerId customer identifier
     */
    public OrderEntity(String customerId) {
        this.customerId = customerId;
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Creates a new order waiting for payment confirmation.
     *
     * @param customerId customer identifier
     * @param expiresAt payment deadline
     * @return pending payment order
     */
    public static OrderEntity pendingPayment(String customerId, Instant expiresAt) {
        OrderEntity order = new OrderEntity(customerId);
        order.status = OrderStatus.PENDING_PAYMENT;
        order.expiresAt = expiresAt;
        order.updatedAt = Instant.now();
        return order;
    }

    /**
     * Adds a line item to the order.
     *
     * @param sku stock keeping unit
     * @param quantity requested units
     */
    public void addItem(String sku, int quantity) {
        items.add(new OrderItemEntity(this, sku, quantity));
        updatedAt = Instant.now();
    }

    /**
     * Moves the order to a new status.
     *
     * @param newStatus next workflow status
     */
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the order identifier.
     *
     * @return order id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the customer identifier.
     *
     * @return customer id
     */
    public String getCustomerId() {
        return customerId;
    }

    /**
     * Returns the current order status.
     *
     * @return order status
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Returns the order creation time.
     *
     * @return created timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when the order last changed.
     *
     * @return updated timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns the payment deadline for pending orders.
     *
     * @return expiration timestamp
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Returns order line items.
     *
     * @return immutable line item list
     */
    public List<OrderItemEntity> getItems() {
        return Collections.unmodifiableList(items);
    }
}
