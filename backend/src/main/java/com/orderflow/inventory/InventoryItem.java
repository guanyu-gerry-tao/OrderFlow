package com.orderflow.inventory;

import com.orderflow.api.WorkflowConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Stores available inventory for one SKU.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() {
    }

    /**
     * Creates inventory for one SKU.
     *
     * @param sku stock keeping unit
     * @param availableQuantity available units
     */
    public InventoryItem(String sku, int availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
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
     * Returns currently available inventory.
     *
     * @return available units
     */
    public int getAvailableQuantity() {
        return availableQuantity;
    }

    /**
     * Replaces the available quantity for seed data.
     *
     * @param availableQuantity new available quantity
     */
    public void replaceAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
        this.updatedAt = Instant.now();
    }

    /**
     * Reserves units from available inventory.
     *
     * @param quantity units to reserve
     */
    public void reserve(int quantity) {
        if (availableQuantity < quantity) {
            throw new WorkflowConflictException("Insufficient inventory for SKU " + sku);
        }

        availableQuantity = availableQuantity - quantity;
        updatedAt = Instant.now();
    }
}
