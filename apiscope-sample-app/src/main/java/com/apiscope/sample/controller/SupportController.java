package com.apiscope.sample.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Support API — tickets, escalations, SLA tracking, agents, and knowledge base.
 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "ticketId", "TKT-" + System.currentTimeMillis(),
                "status", "OPEN",
                "priority", request.getOrDefault("priority", "MEDIUM"),
                "assignedQueue", "GENERAL"
        ));
    }

    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<Map<String, Object>> getTicket(@PathVariable String ticketId) {
        return ResponseEntity.ok(Map.of(
                "ticketId", ticketId,
                "status", "IN_PROGRESS",
                "priority", "HIGH",
                "assignedAgent", "agent_042",
                "messages", List.of(
                        Map.of("from", "CUSTOMER", "message", "My order hasn't arrived", "timestamp", "2026-04-28T10:00:00Z")
                )
        ));
    }

    @PostMapping("/tickets/{ticketId}/reply")
    public ResponseEntity<Map<String, Object>> replyToTicket(
            @PathVariable String ticketId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("ticketId", ticketId, "messageId", UUID.randomUUID().toString(), "sent", true));
    }

    @PatchMapping("/tickets/{ticketId}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String ticketId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("ticketId", ticketId, "status", request.getOrDefault("status", "OPEN")));
    }

    @PostMapping("/tickets/{ticketId}/escalate")
    public ResponseEntity<Map<String, Object>> escalateTicket(
            @PathVariable String ticketId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "ticketId", ticketId,
                "escalatedTo", request.getOrDefault("escalateTo", "TIER2"),
                "newPriority", "CRITICAL"
        ));
    }

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> listTickets(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("tickets", List.of(), "total", 0));
    }

    @GetMapping("/sla/metrics")
    public ResponseEntity<Map<String, Object>> getSlaMetrics(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String agentId) {
        return ResponseEntity.ok(Map.of(
                "avgFirstResponseMinutes", 14,
                "avgResolutionHours", 6.4,
                "slaMet", "91.2%",
                "breached", 47
        ));
    }

    @GetMapping("/knowledge-base/search")
    public ResponseEntity<Map<String, Object>> searchKnowledgeBase(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(Map.of("articles", List.of(), "total", 0, "query", q));
    }

    @PutMapping("/knowledge-base/articles/{articleId}")
    public ResponseEntity<Map<String, Object>> upsertArticle(
            @PathVariable String articleId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("articleId", articleId, "saved", true, "published", request.getOrDefault("published", false)));
    }

    @GetMapping("/agents/{agentId}/performance")
    public ResponseEntity<Map<String, Object>> getAgentPerformance(
            @PathVariable String agentId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        return ResponseEntity.ok(Map.of(
                "agentId", agentId,
                "ticketsHandled", 342,
                "avgRating", 4.7,
                "resolutionRate", "88.5%",
                "avgHandleTimeMinutes", 22
        ));
    }
}
