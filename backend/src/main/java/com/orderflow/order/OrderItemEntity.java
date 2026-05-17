package com.orderflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Stores one line item belonging to an order.
 */
@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    protected OrderItemEntity() {
    }

    /**
     * Creates one order line item.
     *
     * @param order owning order
     * @param sku stock keeping unit
     * @param quantity requested units
     */
    public OrderItemEntity(OrderEntity order, String sku, int quantity) {
        this.order = order;
        this.sku = sku;
        this.quantity = quantity;
    }

    /**
     * Returns the line item id.
     *
     * @return line item id
     */
    public UUID getId() {
        return id;
    }

    /**
     * Returns the SKU.
     *
     * @return sku
     */
    public String getSku() {
        return sku;
    }

    /**
     * Returns requested units.
     *
     * @return quantity
     */
    public int getQuantity() {
        return quantity;
    }
}
