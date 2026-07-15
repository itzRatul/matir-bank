package com.matirbank.backend.controller;

import com.matirbank.backend.security.LoginAttemptService;
import com.matirbank.backend.service.AuthService;
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
     * Enforces per-client brute-force protection:
     *   - Resolves a composite "client key" from IP + User-Agent fragment.
     *   - If the client is currently blocked (>= 3 failed attempts within 24 h),
     *     returns HTTP 429 immediately — no credential check performed.
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
        // ────────────────────────────────────────────────────────────────

        try {
            String token = authService.login(email, password);

            // Success → reset failure counter for this client
            loginAttemptService.recordSuccess(clientKey);
            return ResponseEntity.ok(Map.of("token", token));

        } catch (Exception ex) {
            // Failure → record attempt; may trigger a 24h block on 3rd failure
            loginAttemptService.recordFailure(clientKey);

            int attempts = loginAttemptService.getAttemptCount(clientKey);
            boolean nowBlocked = loginAttemptService.isBlocked(clientKey);

            if (nowBlocked) {
                // Just got blocked on THIS attempt
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
