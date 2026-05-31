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
 * Inventory &amp; Warehouse API — warehouse management, stock levels, transfers, and alerts.
 * Backed by real H2 database queries via {@link ProductCatalogDao}.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final ProductCatalogDao productCatalogDao;

    public InventoryController(ProductCatalogDao productCatalogDao) {
        this.productCatalogDao = productCatalogDao;
    }

    /** Returns real stock levels from the database, optionally filtered by SKU or warehouse. */
    @GetMapping("/stock")
    public ResponseEntity<Map<String, Object>> getStockLevels(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String warehouseId) {

        List<Product> products;
        if (sku != null && !sku.isBlank()) {
            products = productCatalogDao.findBySku(sku).map(List::of).orElse(List.of());
        } else if (warehouseId != null && !warehouseId.isBlank()) {
            products = productCatalogDao.findByWarehouseId(warehouseId);
        } else {
            products = productCatalogDao.findByStatus("ACTIVE");
        }

        int totalStock = products.stream().mapToInt(p -> p.getStockQuantity() != null ? p.getStockQuantity() : 0).sum();
        List<Map<String, Object>> items = products.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("sku",           p.getSku());
            m.put("name",          p.getName());
            m.put("warehouseId",   p.getWarehouseId());
            m.put("stockQuantity", p.getStockQuantity());
            m.put("reorderLevel",  p.getReorderLevel());
            m.put("status",        p.getStatus());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("sku", sku, "totalStock", totalStock, "items", items));
    }

    @PostMapping("/transfers")
    public ResponseEntity<Map<String, Object>> createTransfer(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "transferId",       UUID.randomUUID().toString(),
                "status",           "PENDING",
                "estimatedArrival", "2026-05-15"
        ));
    }

    @GetMapping("/transfers/{transferId}")
    public ResponseEntity<Map<String, Object>> getTransfer(@PathVariable String transferId) {
        return ResponseEntity.ok(Map.of(
                "transferId", transferId,
                "status",     "IN_TRANSIT",
                "sku",        "P001",
                "quantity",   100
        ));
    }

    @GetMapping("/warehouses")
    public ResponseEntity<Map<String, Object>> listWarehouses() {
        // Derive warehouse list dynamically from product data
        List<String> warehouseIds = productCatalogDao.findAll().stream()
                .map(Product::getWarehouseId)
                .filter(w -> w != null)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        List<Map<String, Object>> warehouses = warehouseIds.stream().map(wid -> {
            long count = productCatalogDao.findByWarehouseId(wid).stream()
                    .mapToInt(p -> p.getStockQuantity() != null ? p.getStockQuantity() : 0).sum();
            Map<String, Object> w = new HashMap<>();
            w.put("warehouseId", wid);
            w.put("totalStock", count);
            return w;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("warehouses", warehouses));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<Map<String, Object>> adjustStock(@RequestBody Map<String, Object> request) {
        String sku = (String) request.getOrDefault("sku", "");
        int delta = request.get("delta") instanceof Number n ? n.intValue() : 0;
        productCatalogDao.findBySku(sku).ifPresent(p -> {
            p.setStockQuantity(Math.max(0, p.getStockQuantity() + delta));
            productCatalogDao.save(p);
        });
        return ResponseEntity.ok(Map.of(
                "adjustmentId", UUID.randomUUID().toString(),
                "sku",    sku,
                "delta",  delta,
                "reason", request.getOrDefault("reason", "AUDIT")
        ));
    }

    /** Returns real low-stock alerts: products whose stock is below the threshold. */
    @GetMapping("/alerts/low-stock")
    public ResponseEntity<Map<String, Object>> getLowStockAlerts(
            @RequestParam(required = false) String warehouseId,
            @RequestParam(defaultValue = "50") int threshold) {

        List<Product> lowStock = productCatalogDao.findByStockQuantityLessThan(threshold);
        List<Map<String, Object>> alerts = lowStock.stream()
                .filter(p -> warehouseId == null || warehouseId.equals(p.getWarehouseId()))
                .map(p -> {
                    Map<String, Object> a = new HashMap<>();
                    a.put("sku",           p.getSku());
                    a.put("name",          p.getName());
                    a.put("warehouseId",   p.getWarehouseId());
                    a.put("stockQuantity", p.getStockQuantity());
                    a.put("reorderLevel",  p.getReorderLevel());
                    return a;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("alerts", alerts, "threshold", threshold, "count", alerts.size()));
    }

    @PutMapping("/reorder-rules")
    public ResponseEntity<Map<String, Object>> setReorderRule(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("ruleId", UUID.randomUUID().toString(), "saved", true));
    }

    @GetMapping("/reports/valuation")
    public ResponseEntity<Map<String, Object>> getValuationReport(
            @RequestParam(required = false) String warehouseId,
            @RequestParam(defaultValue = "FIFO") String valuationMethod) {
        List<Product> products = warehouseId != null
                ? productCatalogDao.findByWarehouseId(warehouseId)
                : productCatalogDao.findAll();
        double totalValue = products.stream()
                .mapToDouble(p -> (p.getPrice() != null ? p.getPrice() : 0.0) *
                                  (p.getStockQuantity() != null ? p.getStockQuantity() : 0))
                .sum();
        return ResponseEntity.ok(Map.of(
                "totalValue",       totalValue,
                "currency",         "USD",
                "valuationMethod",  valuationMethod,
                "asOf",             java.time.LocalDate.now().toString()
        ));
    }

    @PostMapping("/receipts")
    public ResponseEntity<Map<String, Object>> receiveShipment(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "receiptId",       UUID.randomUUID().toString(),
                "purchaseOrderId", request.getOrDefault("purchaseOrderId", ""),
                "status",          "RECEIVED"
        ));
    }

    @GetMapping("/movements")
    public ResponseEntity<Map<String, Object>> getMovements(
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String warehouseId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(Map.of("movements", List.of(), "total", 0));
    }
}
