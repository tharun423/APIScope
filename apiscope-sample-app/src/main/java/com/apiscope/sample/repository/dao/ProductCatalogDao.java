package com.apiscope.sample.repository.dao;

import com.apiscope.sample.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Product}.
 *
 * <p>Custom queries here are used by:
 * <ul>
 *   <li>{@link com.apiscope.sample.repository.InventoryRepository} — AOP-visible REPOSITORY layer</li>
 *   <li>{@link com.apiscope.sample.controller.ProductController} — product CRUD</li>
 *   <li>{@link com.apiscope.sample.controller.InventoryController} — warehouse stock & alerts</li>
 *   <li>{@link com.apiscope.sample.controller.AnalyticsController} — top-selling products</li>
 * </ul>
 */
public interface ProductCatalogDao extends JpaRepository<Product, Long> {

    /** Lookup by SKU (e.g. "P001") — used by the Flow Tracer checkout demo. */
    Optional<Product> findBySku(String sku);

    /** All products in a given category. */
    List<Product> findByCategory(String category);

    /** Products whose current stock is below {@code threshold} — low-stock alerts. */
    List<Product> findByStockQuantityLessThan(int threshold);

    /** Filter by lifecycle status (ACTIVE / DEACTIVATED / DRAFT). */
    List<Product> findByStatus(String status);

    /** All products stored in a specific warehouse. */
    List<Product> findByWarehouseId(String warehouseId);

    /**
     * Atomically decrement stock only when sufficient quantity is available.
     * Returns the number of rows updated (1 = success, 0 = insufficient stock).
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty " +
           "WHERE p.sku = :sku AND p.stockQuantity >= :qty")
    int decrementStock(@Param("sku") String sku, @Param("qty") int qty);

    /**
     * Native query: products ranked by total units sold across all non-cancelled orders.
     * Returns rows of [sku, name, category, total_sold].
     */
    @Query(value = """
            SELECT p.sku, p.name, p.category, COALESCE(SUM(o.quantity), 0) AS total_sold
            FROM product p
            LEFT JOIN sales_order o ON p.id = o.product_id AND o.status != 'CANCELLED'
            WHERE p.status = 'ACTIVE'
            GROUP BY p.id, p.sku, p.name, p.category
            ORDER BY total_sold DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopSellingProducts(@Param("limit") int limit);
}
