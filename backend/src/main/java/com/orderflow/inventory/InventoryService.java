package com.orderflow.inventory;
import org.springframework.stereotype.Service;

/**
 * Handles synchronous inventory setup and reservation.
 */
@Service
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryReservationStrategy inventoryReservationStrategy;

    /**
     * Creates an inventory service.
     *
     * @param inventoryItemRepository inventory repository
     * @param inventoryReservationStrategy configured reservation strategy
     */
    public InventoryService(
            InventoryItemRepository inventoryItemRepository,
            InventoryReservationStrategy inventoryReservationStrategy
    ) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryReservationStrategy = inventoryReservationStrategy;
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
     * Reserves inventory from one SKU for the synchronous workflow.
     *
     * @param sku stock keeping unit
     * @param quantity units to reserve
     */
    public void reserve(String sku, int quantity) {
        inventoryReservationStrategy.reserve(sku, quantity);
    }
}
