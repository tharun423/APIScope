package com.apiscope.sample.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User Management API — registration, profile, roles, deactivation, and search.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "userId", UUID.randomUUID().toString(),
                "email", request.getOrDefault("email", "user@example.com"),
                "status", "PENDING_VERIFICATION"
        ));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "email", "john.doe@example.com",
                "firstName", "John",
                "lastName", "Doe",
                "role", "CUSTOMER",
                "status", "ACTIVE"
        ));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("userId", userId, "updated", true));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deactivateUser(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of("userId", userId, "status", "DEACTIVATED"));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Map.of("results", List.of(), "total", 0, "page", page, "size", size));
    }

    @PostMapping("/{userId}/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("userId", userId, "emailVerified", true));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<Map<String, Object>> assignRole(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("userId", userId, "role", request.getOrDefault("role", "CUSTOMER")));
    }

    @PostMapping("/{userId}/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @PathVariable String userId,
            @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of("userId", userId, "passwordChanged", true));
    }

    @GetMapping("/{userId}/sessions")
    public ResponseEntity<Map<String, Object>> getUserSessions(
            @PathVariable String userId,
            @RequestParam(defaultValue = "true") boolean active) {
        return ResponseEntity.ok(Map.of("userId", userId, "sessions", List.of(), "activeOnly", active));
    }

    @DeleteMapping("/{userId}/sessions")
    public ResponseEntity<Map<String, Object>> revokeAllSessions(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of("userId", userId, "sessionsRevoked", true));
    }
}
