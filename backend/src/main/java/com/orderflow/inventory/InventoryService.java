package com.orderflow.inventory;

import com.orderflow.api.WorkflowConflictException;
import org.springframework.stereotype.Service;

/**
 * Handles synchronous inventory setup and reservation.
 */
@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Creates an inventory service.
     *
     * @param inventoryItemRepository inventory repository
     */
    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Seeds or replaces available inventory for a SKU.
     *
     * @param sku stock keeping unit
     * @param availableQuantity available units
     */
    public void seedInventory(String sku, int availableQuantity) {
        InventoryItem inventoryItem = inventoryItemRepository.findBySku(sku)
                .orElseGet(() -> new InventoryItem(sku, availableQuantity));

        inventoryItem.replaceAvailableQuantity(availableQuantity);
        inventoryItemRepository.save(inventoryItem);
    }

    /**
     * Reserves inventory from one SKU for the M1 synchronous workflow.
     *
     * @param sku stock keeping unit
     * @param quantity units to reserve
     */
    public void reserve(String sku, int quantity) {
        InventoryItem inventoryItem = inventoryItemRepository.findBySku(sku)
                .orElseThrow(() -> new WorkflowConflictException("Inventory SKU not found: " + sku));

        inventoryItem.reserve(quantity);
        inventoryItemRepository.save(inventoryItem);
    }
}
