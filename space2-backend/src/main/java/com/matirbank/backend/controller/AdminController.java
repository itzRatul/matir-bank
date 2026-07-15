package com.matirbank.backend.controller;

import com.matirbank.backend.model.SystemConfig;
import com.matirbank.backend.repository.SystemConfigRepository;
import com.matirbank.backend.security.UserPrincipal;
import com.matirbank.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    /** Get current user's profile */
    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getUserDecrypted(principal.getUser().getId()));
    }

    /** List all customers — Admin/Manager */
    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getAllCustomers() {
        return ResponseEntity.ok(userService.getAllCustomers());
    }

    /** List all admins — Manager only */
    @GetMapping("/admins")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getAllAdmins() {
        return ResponseEntity.ok(userService.getAllAdmins());
    }

    /** Create an admin — Manager only */
    @PostMapping("/admins")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> createAdmin(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        if (name == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name, email, password required"));
        }
        return ResponseEntity.ok(userService.createAdmin(name, email, password));
    }

    /** Delete an admin — Manager only */
    @DeleteMapping("/admins/{userId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> deleteAdmin(@PathVariable Long userId) {
        userService.deleteAdmin(userId);
        return ResponseEntity.ok(Map.of("message", "Admin deleted"));
    }

    /** Update system config — Manager only */
    @PutMapping("/config")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, String> body,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        String key = body.get("key");
        String value = body.get("value");
        if (key == null || value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "key and value required"));
        }

        SystemConfig config = systemConfigRepository.findByConfigKey(key)
                .orElse(new SystemConfig(key, value, principal.getUser().getId()));
        config.setConfigValue(value);
        config.setUpdatedBy(principal.getUser().getId());
        systemConfigRepository.save(config);
        return ResponseEntity.ok(Map.of("message", "Config updated", "key", key, "value", value));
    }

    /** Get all system configs — Manager only */
    @GetMapping("/config")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<?> getAllConfig() {
        return ResponseEntity.ok(systemConfigRepository.findAll());
    }
}
