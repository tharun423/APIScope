package com.apiscope.sample.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo Payments &amp; Subscriptions API.
 * All endpoints are indexed by Agentic Docs and available for RAG queries.
 */
@RestController
@RequestMapping("/api/v1")
public class PaymentsController {

    @GetMapping("/subscriptions/{id}")
    public ResponseEntity<Map<String, Object>> getSubscription(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", "ACTIVE",
                "plan", "PREMIUM",
                "daysActive", 10
        ));
    }

    @GetMapping("/accounts/{accountId}/subscriptions")
    public ResponseEntity<Map<String, Object>> listSubscriptions(@PathVariable String accountId) {
        return ResponseEntity.ok(Map.of("accountId", accountId, "subscriptions", List.of()));
    }

    @PostMapping("/subscriptions/{id}/terminate")
    public ResponseEntity<Map<String, Object>> terminateSubscription(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> terminateResponse = new HashMap<>();
        terminateResponse.put("subscriptionId", id);
        terminateResponse.put("status", "TERMINATED");
        terminateResponse.put("refundType", request.get("refundType"));
        return ResponseEntity.ok(terminateResponse);
    }

    @PutMapping("/subscriptions/{id}/plan")
    public ResponseEntity<Map<String, Object>> changePlan(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> planResponse = new HashMap<>();
        planResponse.put("id", id);
        planResponse.put("newPlan", request.get("newPlan"));
        return ResponseEntity.ok(planResponse);
    }

    @PostMapping("/billing/refund/calculate")
    public ResponseEntity<Map<String, Object>> calculateRefund(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("refundAmount", 14.99, "currency", "USD"));
    }

    @PostMapping("/payments/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("transactionId", "TXN-001", "status", "SUCCESS"));
    }

    @GetMapping("/accounts/{accountId}/payments")
    public ResponseEntity<Map<String, Object>> getPaymentHistory(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("accountId", accountId, "page", page, "size", size, "transactions", List.of()));
    }

    @PostMapping("/payments/refund")
    public ResponseEntity<Map<String, Object>> issueRefund(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("refundId", "REF-001", "status", "PROCESSED"));
    }
}
