package com.apiscope.sample.controller;

import com.apiscope.sample.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Order Checkout API — demonstrates a deep multi-layer call chain for Flow Tracer.
 *
 * <p>When you trace {@code POST /api/v1/checkout} in the Flow Tracer tab you will see:
 * <pre>
 * CONTROLLER  OrderFlowController.checkout()
 *   SERVICE   OrderService.validateOrder()
 *   SERVICE   InventoryService.checkAvailability()
 *     REPO    InventoryRepository.findStockByProductId()
 *   SERVICE   InventoryService.reserveInventory()
 *     REPO    InventoryRepository.reserveStock()
 *   SERVICE   PaymentService.validatePayment()
 *     REPO    PaymentRepository.validatePaymentMethod()
 *   SERVICE   PaymentService.authorisePayment()
 *     REPO    PaymentRepository.createPaymentRecord()
 *   SERVICE   OrderService.confirmOrder()
 * </pre>
 *
 * <p>To trigger an error step (red card in Flow Tracer), send:
 * <pre>
 * { "productId": "P001", "quantity": 999, "unitPrice": 29.99, "paymentMethodId": "pm_test_001" }
 * </pre>
 * This causes InventoryService.checkAvailability() to throw an
 * {@link IllegalStateException} (stock = 42, requested = 999).
 */
@RestController
@RequestMapping("/api/v1/checkout")
public class OrderFlowController {

    private final OrderService orderService;

    public OrderFlowController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Place a new order — runs the full checkout pipeline across Controller → Service → Repository layers.
     * Ideal for demonstrating live execution tracing in the Flow Tracer tab.
     *
     * <p>Request body:
     * <pre>
     * {
     *   "productId":       "P001",
     *   "quantity":        2,
     *   "unitPrice":       29.99,
     *   "paymentMethodId": "pm_test_001"
     * }
     * </pre>
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> checkout(@RequestBody Map<String, Object> request) {
        Map<String, Object> order = orderService.checkout(request);
        return ResponseEntity.status(201).body(order);
    }

    /**
     * Simulate a stock-out error — triggers an ERROR step in the Flow Tracer.
     * Sends quantity=999 which exceeds available stock (42), causing InventoryService to throw.
     */
    @PostMapping("/simulate-error")
    public ResponseEntity<Map<String, Object>> simulateError() {
        Map<String, Object> request = Map.of(
                "productId",       "P001",
                "quantity",        999,
                "unitPrice",       29.99,
                "paymentMethodId", "pm_test_001"
        );
        Map<String, Object> order = orderService.checkout(request);
        return ResponseEntity.status(201).body(order);
    }
}
