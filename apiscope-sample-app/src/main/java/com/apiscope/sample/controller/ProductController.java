package com.apiscope.sample.controller;

import com.apiscope.sample.entity.Product;
import com.apiscope.sample.repository.dao.ProductCatalogDao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product Catalog API — product CRUD, search, inventory, pricing, and reviews.
 * Backed by real H2 database queries via {@link ProductCatalogDao}.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductCatalogDao productCatalogDao;

    public ProductController(ProductCatalogDao productCatalogDao) {
        this.productCatalogDao = productCatalogDao;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProduct(@RequestBody Map<String, Object> request) {
        Product p = new Product();
        p.setSku((String) request.getOrDefault("sku", "SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase()));
        p.setName((String) request.getOrDefault("name", "New Product"));
        p.setCategory((String) request.getOrDefault("category", "Uncategorised"));
        p.setPrice(request.get("price") instanceof Number n ? n.doubleValue() : 0.0);
        p.setStockQuantity(request.get("stockQuantity") instanceof Number n ? n.intValue() : 0);
        p.setWarehouseId((String) request.getOrDefault("warehouseId", "WH-EAST-01"));
        p.setReorderLevel(10);
        p.setStatus("DRAFT");
        productCatalogDao.save(p);
        return ResponseEntity.status(201).body(Map.of(
                "productId", p.getId(),
                "sku",       p.getSku(),
                "status",    "DRAFT"
        ));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> getProduct(@PathVariable Long productId) {
        return productCatalogDao.findById(productId)
                .map(p -> ResponseEntity.ok(toMap(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        return productCatalogDao.findById(productId).map(p -> {
            if (request.containsKey("name"))     p.setName((String) request.get("name"));
            if (request.containsKey("price"))    p.setPrice(((Number) request.get("price")).doubleValue());
            if (request.containsKey("category")) p.setCategory((String) request.get("category"));
            productCatalogDao.save(p);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("productId", productId);
            body.put("updated", true);
            return ResponseEntity.<Map<String, Object>>ok(body);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Map<String, Object>> deactivateProduct(@PathVariable Long productId) {
        return productCatalogDao.findById(productId).map(p -> {
            p.setStatus("DEACTIVATED");
            productCatalogDao.save(p);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("productId", productId);
            body.put("status", "DEACTIVATED");
            return ResponseEntity.<Map<String, Object>>ok(body);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Search products — filter by category; price range applied in-memory for simplicity. */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "true") boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "relevance") String sortBy) {

        List<Product> base = (category != null && !category.isBlank())
                ? productCatalogDao.findByCategory(category)
                : productCatalogDao.findAll();

        List<Map<String, Object>> results = base.stream()
                .filter(p -> q == null || p.getName().toLowerCase().contains(q.toLowerCase()) ||
                             p.getSku().toLowerCase().contains(q.toLowerCase()))
                .filter(p -> minPrice == null || p.getPrice() >= minPrice)
                .filter(p -> maxPrice == null || p.getPrice() <= maxPrice)
                .filter(p -> !inStock || p.getStockQuantity() > 0)
                .skip((long) page * size)
                .limit(size)
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("results", results, "total", results.size(), "page", page, "size", size));
    }

    @PatchMapping("/{productId}/stock")
    public ResponseEntity<Map<String, Object>> updateStock(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        return productCatalogDao.findById(productId).map(p -> {
            int newQty = request.get("stockQuantity") instanceof Number n ? n.intValue() : p.getStockQuantity();
            p.setStockQuantity(newQty);
            productCatalogDao.save(p);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("productId", productId);
            body.put("newStockQuantity", newQty);
            return ResponseEntity.<Map<String, Object>>ok(body);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{productId}/stock/history")
    public ResponseEntity<Map<String, Object>> getStockHistory(
            @PathVariable Long productId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(Map.of("productId", productId, "movements", List.of()));
    }

    @PostMapping("/{productId}/reviews")
    public ResponseEntity<Map<String, Object>> addReview(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "reviewId",  UUID.randomUUID().toString(),
                "productId", productId,
                "rating",    request.getOrDefault("rating", 5),
                "status",    "PENDING_MODERATION"
        ));
    }

    @GetMapping("/{productId}/reviews")
    public ResponseEntity<Map<String, Object>> getReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "recent") String sortBy) {
        return ResponseEntity.ok(Map.of(
                "productId",     productId,
                "reviews",       List.of(),
                "averageRating", 4.5,
                "totalReviews",  0
        ));
    }

    @PutMapping("/{productId}/pricing")
    public ResponseEntity<Map<String, Object>> updatePricing(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> request) {
        return productCatalogDao.findById(productId).map(p -> {
            double newPrice = request.get("price") instanceof Number n ? n.doubleValue() : p.getPrice();
            p.setPrice(newPrice);
            productCatalogDao.save(p);
            Map<String, Object> resp = new HashMap<>();
            resp.put("productId",    productId);
            resp.put("price",        newPrice);
            resp.put("salePrice",    request.getOrDefault("salePrice", null));
            resp.put("priceUpdated", true);
            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId",     p.getId());
        m.put("sku",           p.getSku());
        m.put("name",          p.getName());
        m.put("category",      p.getCategory());
        m.put("price",         p.getPrice());
        m.put("stockQuantity", p.getStockQuantity());
        m.put("warehouseId",   p.getWarehouseId());
        m.put("status",        p.getStatus());
        return m;
    }
}
