package com.orderflow.inventory;

/**
 * Reserves inventory using the configured correctness strategy.
 */
public interface InventoryReservationStrategy {

    /**
     * Reserves units from a SKU.
     *
     * @param sku stock keeping unit
     * @param quantity units to reserve
     */
    void reserve(String sku, int quantity);
}
