package com.orderflow.inventory;

import com.orderflow.config.ConditionalOnRuntimeRole;
import com.orderflow.config.RuntimeRole;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides inventory setup endpoints for local demos and integration tests.
 */
@RestController
@RequestMapping("/api/inventory")
@ConditionalOnRuntimeRole(RuntimeRole.API)
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * Creates an inventory controller.
     *
     * @param inventoryService inventory service
     */
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Seeds or replaces local inventory for one SKU.
     *
     * @param request inventory seed request
     * @return empty 204 response when the SKU is ready
     */
    @PostMapping("/seed")
    public ResponseEntity<Void> seedInventory(@Valid @RequestBody SeedInventoryRequest request) {
        inventoryService.seedInventory(request.sku(), request.availableQuantity());
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists current inventory for the operations console.
     *
     * @return inventory rows
     */
    @GetMapping
    public List<InventoryItemResponse> listInventory() {
        return inventoryService.listInventory();
    }
}
