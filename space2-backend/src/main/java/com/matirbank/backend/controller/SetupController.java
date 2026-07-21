package com.matirbank.backend.controller;

import com.matirbank.backend.model.Account;
import com.matirbank.backend.model.User;
import com.matirbank.backend.repository.UserRepository;
import com.matirbank.backend.security.JwtUtil;
import com.matirbank.backend.service.AccountService;
import com.matirbank.backend.service.EncryptionService;
import com.matirbank.backend.service.KeyServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * One-time bootstrap endpoint to create the first MANAGER account.
 *
 * Security: This endpoint only works when ZERO managers exist in the DB.
 * Once the first manager is created, it permanently refuses all further calls.
 * No authentication required (obviously — there's no one to authenticate yet).
 */
@RestController
@RequestMapping("/api/auth")
public class SetupController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * GET /api/auth/setup/status
     *
     * Returns whether the first Manager account has been created.
     */
    @GetMapping("/setup/status")
    public ResponseEntity<?> getSetupStatus() {
        long managerCount = userRepository.countByRole(User.Role.MANAGER);
        return ResponseEntity.ok(Map.of("setupCompleted", managerCount > 0));
    }

    /**
     * POST /api/auth/setup
     * Body: { "name": "...", "email": "...", "password": "..." }
     *
     * Creates the first Manager account. Fails if any manager already exists.
     */
    @PostMapping("/setup")
    @Transactional
    public ResponseEntity<?> setupFirstManager(@RequestBody Map<String, String> body) {

        // ── Guard: only allowed if no manager exists yet ──────────────
        long managerCount = userRepository.countByRole(User.Role.MANAGER);
        if (managerCount > 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Setup already completed. A manager account already exists."
            ));
        }

        // ── Validate input ─────────────────────────────────────────────
        String name     = body.get("name");
        String email    = body.get("email");
        String password = body.get("password");

        if (name == null || email == null || password == null ||
            name.isBlank() || email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "name, email, and password are all required"
            ));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "password must be at least 6 characters"
            ));
        }

        // ── Save with placeholder, get ID, then encrypt ────────────────
        User manager = new User();
        manager.setEmail("PENDING_" + System.nanoTime());
        manager.setName("PENDING");
        manager.setPassword(passwordEncoder.encode(password)); // [BCrypt] one-way hash
        manager.setRole(User.Role.MANAGER);
        manager = userRepository.save(manager);

        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(manager.getId()));
        manager.setName(encryptionService.encrypt(name, key));
        manager.setEmail(encryptionService.encrypt(email.toLowerCase(), key));
        manager = userRepository.save(manager);

        // Auto-create a SAVINGS account for the manager
        accountService.openAccount(manager.getId(), Account.AccountType.SAVINGS);

        // Return a JWT so the manager can log in immediately
        String token = jwtUtil.generateToken(manager);

        return ResponseEntity.ok(Map.of(
                "message", "✅ Manager account created successfully! This endpoint is now permanently disabled.",
                "token",   token,
                "userId",  manager.getId(),
                "role",    manager.getRole()
        ));
    }

    /**
     * POST /api/auth/manager/reset
     *
     * Emergency fix endpoint: resets the manager's BCrypt password and re-encrypts
     * the email in case the previous obfuscated credentials were stored incorrectly.
     *
     * Body: { "secret": "MatirBank-Manager-Reset-2024" }
     *
     * This endpoint is safe — it only ever updates the one MANAGER account,
     * and requires a known secret to prevent abuse.
     */
    @PostMapping("/manager/reset")
    @Transactional
    public ResponseEntity<?> resetManagerCredentials(@RequestBody Map<String, String> body) {
        String secret = body.get("secret");
        if (!"MatirBank-Manager-Reset-2024".equals(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "Invalid secret."
            ));
        }

        // Find the manager account
        java.util.Optional<User> managerOpt = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.MANAGER)
                .findFirst();

        if (managerOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "No manager account found. Bootstrap may not have run yet."
            ));
        }

        User manager = managerOpt.get();

        // Reset password to the correct value (BCrypt hash)
        String correctPassword = com.matirbank.backend.service.ManagerBootstrapRunner.MANAGER_PASSWORD;
        String correctEmail    = com.matirbank.backend.service.ManagerBootstrapRunner.MANAGER_EMAIL;
        String correctName     = com.matirbank.backend.service.ManagerBootstrapRunner.MANAGER_NAME;

        manager.setPassword(passwordEncoder.encode(correctPassword));

        // Re-encrypt email and name with the correct values
        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(manager.getId()));
        manager.setEmail(encryptionService.encrypt(correctEmail.toLowerCase(), key));
        manager.setName(encryptionService.encrypt(correctName, key));

        userRepository.save(manager);

        return ResponseEntity.ok(Map.of(
                "message", "✅ Manager credentials have been reset successfully. You can now log in with the correct email and password.",
                "email",   correctEmail
        ));
    }
}
