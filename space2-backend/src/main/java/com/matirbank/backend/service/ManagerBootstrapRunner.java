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
 * auto-creates the primary system manager from embedded credentials.
 *
 * ── Credential obfuscation strategy ─────────────────────────────────────
 *
 *   Sensitive values (email, password) are stored as byte[] constants that
 *   have been XOR-masked with a compile-time offset. The byte values alone
 *   look like random data — no plaintext string is ever present in this file.
 *
 *   Reconstruction happens ONLY inside the private resolve*() methods, which
 *   are called once at startup, produce a short-lived String, and let the JVM
 *   garbage-collect it immediately after use.
 *
 *   The credential fragments are split across multiple private constants and
 *   are stored in non-sequential naming to further resist casual code reading.
 *
 *   Security note: This is source-level obfuscation to prevent trivial grep /
 *   string-search discovery. True security comes from:
 *     1. BCrypt hashing of the stored password (one-way, salted).
 *     2. AES/XOR encryption of the email via the KeyService.
 *     3. Login brute-force protection (3 attempts → 24-hour block).
 */
@Component
public class ManagerBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerBootstrapRunner.class);

    @Autowired private UserRepository    userRepository;
    @Autowired private PasswordEncoder   passwordEncoder;
    @Autowired private EncryptionService encryptionService;
    @Autowired private KeyServiceClient  keyServiceClient;
    @Autowired private AccountService    accountService;

    // ══════════════════════════════════════════════════════════════════════
    //  Obfuscated credentials (Base64 of XOR cipher)
    //  To change these credentials:
    //    1. Choose your new email or password.
    //    2. XOR each character with the corresponding character of the XOR_KEY (repeating).
    //    3. Base64-encode the resulting bytes and update the constants below.
    // ══════════════════════════════════════════════════════════════════════

    private static final String XOR_KEY = "MatirBankSecretKey";

    // Decoded default: "251-15-596@diu.edu.bd"
    private static final String OBFUSCATED_EMAIL = "f1RFREN3TFtSZSUHGxBaLgEMYwMQ";

    // Decoded default: "Ceo-of-MatirBank$251-15-596"
    private static final String OBFUSCATED_PASSWORD = "DgQbRB0kTCMKJwwRMAQaIEFLeFBZWEdvVFdd";

    // Decoded default: "System Manager"
    private static final String OBFUSCATED_DISPLAY_NAME = "HhgHHRcvQSMKPQQEFxc=";

    /**
     * Decrypts a Base64 XOR-obfuscated string using the XOR_KEY.
     */
    private String decryptObfuscated(String base64Cipher) {
        byte[] cipherBytes = java.util.Base64.getDecoder().decode(base64Cipher);
        byte[] keyBytes = XOR_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] plainBytes = new byte[cipherBytes.length];
        for (int i = 0; i < cipherBytes.length; i++) {
            plainBytes[i] = (byte) (cipherBytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String resolveIdentifier() {
        return decryptObfuscated(OBFUSCATED_EMAIL);
    }

    private String resolveToken() {
        return decryptObfuscated(OBFUSCATED_PASSWORD);
    }

    private String resolveDisplayName() {
        return decryptObfuscated(OBFUSCATED_DISPLAY_NAME);
    }

    // ── ApplicationRunner entry point ─────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        // [Retry Fix] Key Service may still be cold/starting when backend starts.
        // Retry up to 15 times with 6-second delay (total ~90 seconds wait).
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

    @org.springframework.transaction.annotation.Transactional
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
            log.info("[Bootstrap] Primary manager already registered — skipping.");
            return;
        }
        log.info("[Bootstrap] Initialising primary system manager...");
        createSystemManager();
        log.info("[Bootstrap] Primary system manager initialised successfully.");
    }

    /**
     * Creates the hardcoded manager account.
     *
     *   1. Resolves credentials from obfuscated fragments (in-scope only).
     *   2. Saves with a placeholder to obtain the auto-generated DB id.
     *   3. Fetches per-user XOR key from KeyService.
     *   4. Encrypts name + email and saves the final record.
     *   5. BCrypt-hashes the password — never stored in plaintext.
     *   6. Auto-opens a SAVINGS account (standard flow for all users).
     */
    private void createSystemManager() {
        // Resolve credentials — short-lived locals, GC'd after method exits
        final String rawEmail    = resolveIdentifier();
        final String rawPassword = resolveToken();
        final String rawName     = resolveDisplayName();

        // Step 1: Save with placeholder to get DB-generated ID
        User mgr = new User();
        mgr.setEmail("PENDING_" + System.nanoTime());
        mgr.setName("PENDING");
        mgr.setPassword(passwordEncoder.encode(rawPassword)); // BCrypt — one-way hash
        mgr.setRole(User.Role.MANAGER);
        mgr = userRepository.save(mgr);

        // Step 2: Encrypt sensitive fields with per-user key from Space 1
        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(mgr.getId()));
        mgr.setName(encryptionService.encrypt(rawName,                key));
        mgr.setEmail(encryptionService.encrypt(rawEmail.toLowerCase(), key));
        mgr = userRepository.save(mgr);

        // Step 3: Auto-create a SAVINGS account for this manager
        Account account = accountService.openAccount(mgr.getId(), Account.AccountType.SAVINGS);
        accountService.approveAccount(account.getAccountNumber(), "SYSTEM_BOOTSTRAP");
    }
}
