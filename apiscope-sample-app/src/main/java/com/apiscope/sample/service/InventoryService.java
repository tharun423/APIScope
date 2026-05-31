package com.apiscope.sample.service;

import com.apiscope.sample.repository.InventoryRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Inventory service — validates stock availability and reserves items before order placement.
 * Shown as SERVICE layer in the Flow Tracer execution diagram.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Check whether the requested quantity is in stock for a product.
     *
     * @param productId the product to check
     * @param quantity  how many units are required
     * @return availability result with current stock level
     * @throws IllegalStateException if insufficient stock is available
     */
    public Map<String, Object> checkAvailability(String productId, int quantity) {
        Map<String, Object> stock = inventoryRepository.findStockByProductId(productId);
        int available = (int) stock.get("available");

        if (available < quantity) {
            throw new IllegalStateException(
                "Insufficient stock for product " + productId +
                ": requested=" + quantity + ", available=" + available);
        }

        return Map.of(
                "productId",  productId,
                "requested",  quantity,
                "available",  available,
                "inStock",    true
        );
    }

    /**
     * Reserve inventory for an order line item.
     * Must be called after {@link #checkAvailability} confirms stock.
     *
     * @param productId the product to reserve
     * @param quantity  the number of units to reserve
     * @return reservation confirmation
     */
    public Map<String, Object> reserveInventory(String productId, int quantity) {
        return inventoryRepository.reserveStock(productId, quantity);
    }
}
