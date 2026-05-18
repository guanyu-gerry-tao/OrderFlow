package com.orderflow.inventory;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Reserves inventory only when the row still has the expected version and enough stock.
     *
     * @param sku stock keeping unit
     * @param quantity units to reserve
     * @param expectedVersion inventory version observed by the caller
     * @param updatedAt update timestamp
     * @return number of updated rows
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE InventoryItem inventoryItem
            SET inventoryItem.availableQuantity = inventoryItem.availableQuantity - :quantity,
                inventoryItem.version = inventoryItem.version + 1,
                inventoryItem.updatedAt = :updatedAt
            WHERE inventoryItem.sku = :sku
              AND inventoryItem.version = :expectedVersion
              AND inventoryItem.availableQuantity >= :quantity
            """)
    int reserveWithExpectedVersion(
            @Param("sku") String sku,
            @Param("quantity") int quantity,
            @Param("expectedVersion") int expectedVersion,
            @Param("updatedAt") Instant updatedAt
    );
}
