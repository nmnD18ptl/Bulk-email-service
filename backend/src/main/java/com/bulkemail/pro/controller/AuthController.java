package com.bulkemail.pro.controller;

import com.bulkemail.pro.model.entity.User;
import com.bulkemail.pro.security.TenantContext;
import com.bulkemail.pro.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result = authService.register(
                body.get("organizationName"),
                body.get("email"),
                body.get("password"),
                body.get("firstName"),
                body.get("lastName")
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result = authService.login(body.get("email"), body.get("password"));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> inviteUser(@RequestBody Map<String, String> body) {
        Long orgId = TenantContext.getOrganizationId();
        try {
            Map<String, Object> result = authService.inviteUser(
                orgId,
                body.get("email"),
                body.getOrDefault("role", "OPERATOR"),
                body.get("firstName"),
                body.get("lastName")
            );
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<List<User>> getUsers() {
        Long orgId = TenantContext.getOrganizationId();
        return ResponseEntity.ok(authService.getOrgUsers(orgId));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<Void> removeUser(@PathVariable Long userId) {
        Long orgId = TenantContext.getOrganizationId();
        try {
            authService.removeUser(orgId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        String email = TenantContext.getUserEmail();
        Long orgId = TenantContext.getOrganizationId();
        String role = TenantContext.getUserRole();
        if (email == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of(
            "email", email,
            "organizationId", orgId != null ? orgId : 0,
            "role", role != null ? role : ""
        ));
    }
}
