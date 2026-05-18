package com.orderflow.inventory;

import com.orderflow.api.WorkflowConflictException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Uses compare-and-set style SQL updates to prevent concurrent inventory oversell.
 */
@Component
@ConditionalOnProperty(
        name = "orderflow.inventory.strategy",
        havingValue = "optimistic-locking",
        matchIfMissing = true
)
public class OptimisticInventoryReservationStrategy implements InventoryReservationStrategy {

    private static final int MAX_ATTEMPTS = 10;

    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Creates the optimistic inventory strategy.
     *
     * @param inventoryItemRepository inventory repository
     */
    public OptimisticInventoryReservationStrategy(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Reserves inventory by retrying when another transaction changes the same row first.
     *
     * @param sku stock keeping unit
     * @param quantity units to reserve
     */
    @Override
    public void reserve(String sku, int quantity) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InventoryItem inventoryItem = inventoryItemRepository.findBySku(sku)
                    .orElseThrow(() -> new WorkflowConflictException("Inventory SKU not found: " + sku));

            if (inventoryItem.getAvailableQuantity() < quantity) {
                throw new WorkflowConflictException("Insufficient inventory for SKU " + sku);
            }

            int updatedRows = inventoryItemRepository.reserveWithExpectedVersion(
                    sku,
                    quantity,
                    inventoryItem.getVersion(),
                    Instant.now()
            );
            if (updatedRows == 1) {
                return;
            }
        }

        throw new WorkflowConflictException("Inventory changed while reserving SKU " + sku + ". Please retry.");
    }
}
