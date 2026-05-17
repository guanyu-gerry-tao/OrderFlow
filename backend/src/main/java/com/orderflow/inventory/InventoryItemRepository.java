package com.orderflow.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries inventory rows.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, String> {

    /**
     * Finds an inventory row by SKU.
     *
     * @param sku stock keeping unit
     * @return matching inventory if it exists
     */
    Optional<InventoryItem> findBySku(String sku);
}
