package com.orderflow.inventory;

import com.orderflow.api.WorkflowConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Baseline inventory strategy used only by benchmark and eval profiles.
 */
@Component
@ConditionalOnProperty(name = "orderflow.inventory.strategy", havingValue = "naive")
public class NaiveInventoryReservationStrategy implements InventoryReservationStrategy {

    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Creates the baseline reservation strategy.
     *
     * @param inventoryItemRepository inventory repository
     */
    public NaiveInventoryReservationStrategy(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Reserves inventory with a read-then-write flow for baseline comparison.
     *
     * @param sku stock keeping unit
     * @param quantity units to reserve
     */
    @Override
    public void reserve(String sku, int quantity) {
        InventoryItem inventoryItem = inventoryItemRepository.findBySku(sku)
                .orElseThrow(() -> new WorkflowConflictException("Inventory SKU not found: " + sku));

        inventoryItem.reserve(quantity);
        inventoryItemRepository.save(inventoryItem);
    }
}
