package com.apiscope.sample.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification Service API — send, schedule, and manage email/SMS/push notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @PostMapping("/email/send")
    public ResponseEntity<Map<String, Object>> sendEmail(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "notificationId", UUID.randomUUID().toString(),
                "channel", "EMAIL",
                "status", "QUEUED",
                "to", request.getOrDefault("to", "")
        ));
    }

    @PostMapping("/sms/send")
    public ResponseEntity<Map<String, Object>> sendSms(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "notificationId", UUID.randomUUID().toString(),
                "channel", "SMS",
                "status", "QUEUED"
        ));
    }

    @PostMapping("/push/send")
    public ResponseEntity<Map<String, Object>> sendPush(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "notificationId", UUID.randomUUID().toString(),
                "channel", "PUSH",
                "status", "QUEUED",
                "devicesTargeted", 1
        ));
    }

    @PostMapping("/schedule")
    public ResponseEntity<Map<String, Object>> scheduleNotification(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "scheduleId", UUID.randomUUID().toString(),
                "scheduledAt", request.getOrDefault("scheduledAt", ""),
                "status", "SCHEDULED"
        ));
    }

    @DeleteMapping("/schedule/{scheduleId}")
    public ResponseEntity<Map<String, Object>> cancelScheduled(@PathVariable String scheduleId) {
        return ResponseEntity.ok(Map.of("scheduleId", scheduleId, "cancelled", true));
    }

    @GetMapping("/{notificationId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String notificationId) {
        return ResponseEntity.ok(Map.of(
                "notificationId", notificationId,
                "status", "DELIVERED",
                "deliveredAt", "2026-04-29T12:05:00Z",
                "channel", "EMAIL"
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam String userId,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("userId", userId, "notifications", List.of(), "total", 0));
    }

    @PutMapping("/templates/{templateId}")
    public ResponseEntity<Map<String, Object>> upsertTemplate(
            @PathVariable String templateId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("templateId", templateId, "saved", true));
    }

    @PutMapping("/preferences/{userId}")
    public ResponseEntity<Map<String, Object>> updatePreferences(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("userId", userId, "preferencesUpdated", true));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String templateId) {
        return ResponseEntity.ok(Map.of(
                "sent", 15000,
                "delivered", 14800,
                "bounced", 50,
                "opened", 8200,
                "openRate", "55.4%"
        ));
    }
}
