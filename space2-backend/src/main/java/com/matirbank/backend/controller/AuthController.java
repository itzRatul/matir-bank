package com.matirbank.backend.controller;

import com.matirbank.backend.security.LoginAttemptService;
import com.matirbank.backend.service.AuthService;
import com.matirbank.backend.service.KeyServiceClient;
import com.matirbank.backend.service.ManagerBootstrapRunner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    @Autowired
    private ManagerBootstrapRunner bootstrapRunner;


    @GetMapping("/debug-connection")
    public ResponseEntity<?> debugConnection() {
        java.util.Map<String, Object> report = new java.util.HashMap<>();
        
        // 1. Test Key Service connection
        try {
            String testKey = keyServiceClient.getOrCreateKey("debug", "test-connection-id");
            report.put("keyServiceStatus", "SUCCESS");
            report.put("keyServiceReceivedValue", testKey);
        } catch (Exception e) {
            report.put("keyServiceStatus", "FAILED");
            report.put("keyServiceError", e.getMessage());
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            report.put("keyServiceStackTrace", sw.toString());
        }

        // 2. Test SQLite Database
        try {
            long userCount = authService.registerCount();
            report.put("databaseStatus", "SUCCESS");
            report.put("databaseUserCount", userCount);
        } catch (Exception e) {
            report.put("databaseStatus", "FAILED");
            report.put("databaseError", e.getMessage());
        }

        return ResponseEntity.ok(report);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String name     = body.get("name");
        String email    = body.get("email");
        String password = body.get("password");
        String nid      = body.get("nid");
        String phone    = body.get("phone");
        String address  = body.get("address");

        if (name == null || email == null || password == null || nid == null || phone == null || address == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "name, email, password, nid, phone, and address are required"));
        }

        authService.register(name, email, password, nid, phone, address);
        return ResponseEntity.ok(Map.of("message", "Registration successful. Please log in."));
    }

    /**
     * POST /api/auth/login
     *
     * Enforces per-client brute-force protection for non-manager accounts.
     * Manager login always bypasses the block (no attempt limit for the manager).
     *
     *   - Resolves a composite "client key" from IP + User-Agent fragment.
     *   - If the client is currently blocked (>= 3 failed attempts within 24 h),
     *     returns HTTP 429 immediately — UNLESS the login email is the manager email.
     *   - On login success: clears the failure counter for this client.
     *   - On login failure: records the attempt; on the 3rd failure, a 24-hour
     *     block is applied to that IP + browser combination.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletRequest request) {
        String email    = body.get("email");
        String password = body.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password are required"));
        }

        // ── Brute-force guard ────────────────────────────────────────────
        // Manager email is never blocked — always allow manager to try.
        boolean isManagerLogin = ManagerBootstrapRunner.MANAGER_EMAIL.equalsIgnoreCase(email.trim());

        if (!isManagerLogin) {
            String clientKey = LoginAttemptService.buildClientKey(request);

            if (loginAttemptService.isBlocked(clientKey)) {
                long minutesLeft = loginAttemptService.getBlockRemainingMinutes(clientKey);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "error",   "Too many failed attempts. Your access is blocked.",
                        "message", "You have been temporarily blocked due to " +
                                   "3 consecutive failed login attempts. " +
                                   "Please try again in " + minutesLeft + " minute(s).",
                        "blockedMinutesRemaining", minutesLeft
                ));
            }
        }
        // ────────────────────────────────────────────────────────────────

        try {
            String token = authService.login(email, password);

            // Success → reset failure counter for this client (if applicable)
            if (!isManagerLogin) {
                loginAttemptService.recordSuccess(LoginAttemptService.buildClientKey(request));
            }
            return ResponseEntity.ok(Map.of("token", token));

        } catch (Exception ex) {
            if (isManagerLogin) {
                // Manager login failed — return generic error, no blocking
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "error",   "Invalid credentials",
                        "message", "Invalid email or password."
                ));
            }

            // Regular user: record attempt; may trigger a 24h block on 3rd failure
            String clientKey = LoginAttemptService.buildClientKey(request);
            loginAttemptService.recordFailure(clientKey);

            int attempts = loginAttemptService.getAttemptCount(clientKey);
            boolean nowBlocked = loginAttemptService.isBlocked(clientKey);

            if (nowBlocked) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "error",   "Account locked due to too many failed attempts.",
                        "message", "3 consecutive failed login attempts detected. " +
                                   "This browser and IP address have been blocked for 24 hours.",
                        "blockedMinutesRemaining", loginAttemptService.getBlockRemainingMinutes(clientKey)
                ));
            }

            int remaining = 3 - attempts;
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error",            "Invalid credentials",
                    "message",          "Invalid email or password. " +
                                        remaining + " attempt(s) remaining before a 24-hour block.",
                    "attemptsLeft",     remaining
            ));
        }
    }
}
