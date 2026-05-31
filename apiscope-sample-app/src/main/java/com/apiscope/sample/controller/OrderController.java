package com.apiscope.sample.controller;

import com.apiscope.sample.entity.SalesOrder;
import com.apiscope.sample.repository.dao.SalesOrderDao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order Management API — create, track, cancel, and return orders.
 * Backed by real H2 database queries via {@link SalesOrderDao}.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final SalesOrderDao salesOrderDao;

    public OrderController(SalesOrderDao salesOrderDao) {
        this.salesOrderDao = salesOrderDao;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "orderId",           UUID.randomUUID().toString(),
                "status",            "PENDING",
                "estimatedDelivery", "2026-05-15"
        ));
    }

    /** Returns a real order from the database by its numeric ID or order reference. */
    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId) {
        return salesOrderDao.findByOrderRef(orderId)
                .map(o -> ResponseEntity.ok(toMap(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Returns all real orders placed by a customer (by customer ID). */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Map<String, Object>> getCustomerOrders(
            @PathVariable Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<SalesOrder> orders = salesOrderDao.findByCustomerId(customerId);
        List<Map<String, Object>> filtered = orders.stream()
                .filter(o -> status == null || o.getStatus().equalsIgnoreCase(status))
                .skip((long) page * size)
                .limit(size)
                .map(this::toMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("customerId", customerId, "orders", filtered, "total", filtered.size()));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> request) {
        return salesOrderDao.findByOrderRef(orderId).map(o -> {
            o.setStatus("CANCELLED");
            salesOrderDao.save(o);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("orderId", orderId);
            body.put("status", "CANCELLED");
            body.put("refundInitiated", true);
            return ResponseEntity.<Map<String, Object>>ok(body);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{orderId}/shipping-address")
    public ResponseEntity<Map<String, Object>> updateShippingAddress(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("orderId", orderId, "addressUpdated", true));
    }

    @PostMapping("/{orderId}/return")
    public ResponseEntity<Map<String, Object>> initiateReturn(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "returnId",    UUID.randomUUID().toString(),
                "orderId",     orderId,
                "status",      "RETURN_INITIATED",
                "returnLabel", "https://returns.example.com/label/RL001"
        ));
    }

    @GetMapping("/{orderId}/tracking")
    public ResponseEntity<Map<String, Object>> getTracking(@PathVariable String orderId) {
        return salesOrderDao.findByOrderRef(orderId).map(o -> {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("orderId",           orderId);
            body.put("status",            o.getStatus());
            body.put("carrier",           "FedEx");
            body.put("trackingNumber",    "TRK" + orderId.replace("-", "").substring(3));
            body.put("estimatedDelivery", o.getCreatedDate() != null ? o.getCreatedDate().plusDays(3).toString() : "N/A");
            return ResponseEntity.<Map<String, Object>>ok(body);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/apply-coupon")
    public ResponseEntity<Map<String, Object>> applyCoupon(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("orderId", orderId, "discount", 10.00, "newTotal", 49.98));
    }

    @GetMapping("/{orderId}/invoice")
    public ResponseEntity<Map<String, Object>> getInvoice(@PathVariable String orderId) {
        return ResponseEntity.ok(Map.of(
                "orderId",      orderId,
                "invoiceUrl",   "https://invoices.example.com/INV-" + orderId + ".pdf",
                "generatedAt",  "2026-04-29T12:00:00Z"
        ));
    }

    @PostMapping("/{orderId}/reorder")
    public ResponseEntity<Map<String, Object>> reorder(@PathVariable String orderId) {
        return ResponseEntity.status(201).body(Map.of(
                "newOrderId",        UUID.randomUUID().toString(),
                "clonedFromOrderId", orderId,
                "status",            "PENDING"
        ));
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private Map<String, Object> toMap(SalesOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderId",     o.getOrderRef());
        m.put("customerId", o.getCustomerId());
        m.put("productId",  o.getProductId());
        m.put("quantity",   o.getQuantity());
        m.put("unitPrice",  o.getUnitPrice());
        m.put("total",      o.getTotalAmount());
        m.put("status",     o.getStatus());
        m.put("createdDate",o.getCreatedDate() != null ? o.getCreatedDate().toString() : null);
        return m;
    }
}
