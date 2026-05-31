package com.apiscope.sample.repository;

import com.apiscope.sample.entity.Product;
import com.apiscope.sample.repository.dao.ProductCatalogDao;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * AOP-visible REPOSITORY layer for inventory operations.
 * Delegates to {@link ProductCatalogDao} (Spring Data JPA) to execute real SQL against
 * the H2 in-memory database seeded by {@code data.sql}.
 *
 * <p>This class uses {@code @Repository} so that {@code FlowAspect} intercepts its
 * methods and shows a REPOSITORY step in the Flow Tracer execution diagram.
 */
@Repository
public class InventoryRepository {

    private final ProductCatalogDao productCatalogDao;

    public InventoryRepository(ProductCatalogDao productCatalogDao) {
        this.productCatalogDao = productCatalogDao;
    }

    /**
     * Look up current stock level for a product by its SKU.
     * Executes: {@code SELECT * FROM product WHERE sku = ?}
     *
     * @param productSku the product SKU (e.g. "P001")
     * @return stock record with available quantity and warehouse location
     */
    public Map<String, Object> findStockByProductId(String productSku) {
        Product product = productCatalogDao.findBySku(productSku)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productSku));
        return Map.of(
                "productId",    product.getSku(),
                "name",         product.getName(),
                "available",    product.getStockQuantity(),
                "reserved",     0,
                "warehouse",    product.getWarehouseId() != null ? product.getWarehouseId() : "WH-EAST-01",
                "reorderLevel", product.getReorderLevel() != null ? product.getReorderLevel() : 10
        );
    }

    /**
     * Atomically reserve (decrement) stock for an order line item.
     * Executes:
     * {@code UPDATE product SET stock_quantity = stock_quantity - ? WHERE sku = ? AND stock_quantity >= ?}
     *
     * @param productSku the product SKU
     * @param quantity   how many units to reserve
     * @return reservation confirmation with a reservation token
     * @throws IllegalStateException if stock is insufficient
     */
    @Transactional
    public Map<String, Object> reserveStock(String productSku, int quantity) {
        int updated = productCatalogDao.decrementStock(productSku, quantity);
        if (updated == 0) {
            throw new IllegalStateException(
                    "Could not reserve " + quantity + " unit(s) for " + productSku + " — insufficient stock");
        }
        return Map.of(
                "productId",        productSku,
                "quantityReserved", quantity,
                "reservationToken", "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "expiresInSeconds", 300
        );
    }
}
