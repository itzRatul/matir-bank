package com.matirbank.backend.service;

import com.matirbank.backend.model.Account;
import com.matirbank.backend.model.User;
import com.matirbank.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ManagerBootstrapRunner
 *
 * Runs once at application startup. If no MANAGER account exists in the DB,
 * auto-creates the primary system manager from credentials below.
 *
 * Manager credentials:
 *   Email:    251-15-596@diu.edu.bd
 *   Password: Ceo-of-MatirBank$251-15-596
 *
 * Security: Password is BCrypt-hashed before storage (never plaintext in DB).
 *           Email is XOR-encrypted via KeyService before storage.
 *
 * [Retry Fix] Key Service may still be cold/starting when backend starts.
 * Retries up to 15 times with 6-second delay (~90 seconds total wait).
 */
@Component
public class ManagerBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerBootstrapRunner.class);

    // ── Manager credentials ───────────────────────────────────────────────
    //  Email:    251-15-596@diu.edu.bd
    //  Password: Ceo-of-MatirBank$251-15-596
    //  Name:     System Manager
    // ─────────────────────────────────────────────────────────────────────
    static final String MANAGER_EMAIL    = "251-15-596@diu.edu.bd";
    static final String MANAGER_PASSWORD = "Ceo-of-MatirBank$251-15-596";
    static final String MANAGER_NAME     = "System Manager";

    @Autowired private UserRepository    userRepository;
    @Autowired private PasswordEncoder   passwordEncoder;
    @Autowired private EncryptionService encryptionService;
    @Autowired private KeyServiceClient  keyServiceClient;
    @Autowired private AccountService    accountService;

    // ── ApplicationRunner entry point ─────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        int maxRetries = 15;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[Bootstrap] Attempt {}/{} — checking system state...", attempt, maxRetries);
                attemptBootstrap();
                return; // success — stop retrying
            } catch (Exception ex) {
                log.warn("[Bootstrap] Attempt {}/{} failed: {}", attempt, maxRetries, ex.getMessage());
                if (attempt < maxRetries) {
                    try { Thread.sleep(6000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                } else {
                    log.error("[Bootstrap] CRITICAL — all {} attempts failed. Manager was NOT initialised.", maxRetries);
                }
            }
        }
    }

    @Transactional
    public void attemptBootstrap() {
        // Clean up any broken PENDING_ manager left from a previous failed bootstrap
        userRepository.findAll().stream()
            .filter(u -> u.getRole() == User.Role.MANAGER &&
                         u.getEmail() != null && u.getEmail().startsWith("PENDING_"))
            .forEach(u -> {
                log.warn("[Bootstrap] Removing broken PENDING_ manager (id={}).", u.getId());
                userRepository.delete(u);
            });

        long existing = userRepository.countByRole(User.Role.MANAGER);
        if (existing > 0) {
            log.info("[Bootstrap] Primary manager found — verifying credentials...");
            autoFixManagerIfNeeded();
            return;
        }
        log.info("[Bootstrap] Initialising primary system manager...");
        createSystemManager();
        log.info("[Bootstrap] Primary system manager initialised successfully.");
    }

    /**
     * Auto-verifies the existing manager's BCrypt password and encrypted email.
     * If either is wrong (e.g. from a previous broken bootstrap), silently fixes them.
     * This runs on EVERY startup — no manual API call needed.
     */
    private void autoFixManagerIfNeeded() {
        userRepository.findAll().stream()
            .filter(u -> u.getRole() == User.Role.MANAGER)
            .findFirst()
            .ifPresent(manager -> {
                boolean needsFix = false;

                // Check 1: Does the stored BCrypt hash match the correct password?
                if (!passwordEncoder.matches(MANAGER_PASSWORD, manager.getPassword())) {
                    log.warn("[Bootstrap] Manager password mismatch detected — resetting to correct value.");
                    manager.setPassword(passwordEncoder.encode(MANAGER_PASSWORD));
                    needsFix = true;
                }

                // Check 2: Does the decrypted email match the correct email?
                try {
                    String key = keyServiceClient.getOrCreateKey("user", String.valueOf(manager.getId()));
                    String decryptedEmail = encryptionService.decrypt(manager.getEmail(), key);
                    if (!MANAGER_EMAIL.equalsIgnoreCase(decryptedEmail)) {
                        log.warn("[Bootstrap] Manager email mismatch detected — re-encrypting correct email.");
                        manager.setEmail(encryptionService.encrypt(MANAGER_EMAIL.toLowerCase(), key));
                        manager.setName(encryptionService.encrypt(MANAGER_NAME, key));
                        needsFix = true;
                    }
                } catch (Exception e) {
                    log.warn("[Bootstrap] Could not verify manager email ({}), re-encrypting.", e.getMessage());
                    try {
                        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(manager.getId()));
                        manager.setEmail(encryptionService.encrypt(MANAGER_EMAIL.toLowerCase(), key));
                        manager.setName(encryptionService.encrypt(MANAGER_NAME, key));
                        needsFix = true;
                    } catch (Exception e2) {
                        log.error("[Bootstrap] Failed to re-encrypt manager email: {}", e2.getMessage());
                    }
                }

                if (needsFix) {
                    userRepository.save(manager);
                    log.info("[Bootstrap] Manager credentials auto-fixed successfully.");
                } else {
                    log.info("[Bootstrap] Manager credentials OK — no fix needed.");
                }
            });
    }

    /**
     * Creates the manager account.
     *
     *   1. Saves with a placeholder to obtain the auto-generated DB id.
     *   2. Fetches per-user XOR key from KeyService.
     *   3. Encrypts name + email and saves the final record.
     *   4. BCrypt-hashes the password — never stored in plaintext.
     *   5. Auto-opens a SAVINGS account (standard flow for all users).
     */
    private void createSystemManager() {
        // Step 1: Save with placeholder to get DB-generated ID
        User mgr = new User();
        mgr.setEmail("PENDING_" + System.nanoTime());
        mgr.setName("PENDING");
        mgr.setPassword(passwordEncoder.encode(MANAGER_PASSWORD)); // BCrypt — one-way hash
        mgr.setRole(User.Role.MANAGER);
        mgr = userRepository.save(mgr);

        // Step 2: Encrypt sensitive fields with per-user key from Space 1
        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(mgr.getId()));
        mgr.setName(encryptionService.encrypt(MANAGER_NAME, key));
        mgr.setEmail(encryptionService.encrypt(MANAGER_EMAIL.toLowerCase(), key));
        mgr = userRepository.save(mgr);

        // Step 3: Auto-create a SAVINGS account for this manager
        Account account = accountService.openAccount(mgr.getId(), Account.AccountType.SAVINGS);
        accountService.approveAccount(account.getAccountNumber(), "SYSTEM_BOOTSTRAP");
    }
}
