package com.matirbank.backend.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LoginAttemptService
 *
 * Tracks failed login attempts per client key (IP + browser fingerprint).
 * After MAX_ATTEMPTS consecutive failures, the client is blocked for BLOCK_DURATION_MS.
 *
 * Storage: in-memory ConcurrentHashMap — fast, no DB dependency.
 * The key is a composite of: IP address + a partial hash of User-Agent.
 * This blocks both the IP and the specific browser session simultaneously.
 *
 * Thread-safe: ConcurrentHashMap + synchronized block on per-key operations.
 */
@Service
public class LoginAttemptService {

    // ── Internal record: tracks attempts + block time for one client key ──
    private static class AttemptRecord {
        int   count;
        long  blockedUntilEpochMs; // 0 means not blocked

        AttemptRecord() {
            this.count              = 0;
            this.blockedUntilEpochMs = 0L;
        }
    }

    // Maximum consecutive failed logins before locking out the client
    private static final int  MAX_ATTEMPTS      = 3;

    // Block duration: 24 hours in milliseconds
    private static final long BLOCK_DURATION_MS = 24L * 60L * 60L * 1000L;

    // In-memory store: clientKey → AttemptRecord
    // ConcurrentHashMap is safe for concurrent reads/writes across threads
    private final ConcurrentHashMap<String, AttemptRecord> _store = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Call this when a login attempt FAILS for the given client key.
     * Increments the failure counter. If it reaches MAX_ATTEMPTS, sets a 24h block.
     */
    public void recordFailure(String clientKey) {
        AttemptRecord rec = _store.computeIfAbsent(clientKey, k -> new AttemptRecord());
        synchronized (rec) {
            // If already blocked, just keep the block (don't reset counter)
            if (rec.blockedUntilEpochMs > 0 && Instant.now().toEpochMilli() < rec.blockedUntilEpochMs) {
                return;
            }
            rec.count++;
            if (rec.count >= MAX_ATTEMPTS) {
                rec.blockedUntilEpochMs = Instant.now().toEpochMilli() + BLOCK_DURATION_MS;
            }
        }
    }

    /**
     * Call this when a login attempt SUCCEEDS. Resets the failure counter.
     */
    public void recordSuccess(String clientKey) {
        _store.remove(clientKey);
    }

    /**
     * Returns true if this client key is currently blocked.
     * Also auto-expires blocks that have passed their 24h window.
     */
    public boolean isBlocked(String clientKey) {
        AttemptRecord rec = _store.get(clientKey);
        if (rec == null) return false;
        synchronized (rec) {
            if (rec.blockedUntilEpochMs == 0) return false;
            if (Instant.now().toEpochMilli() >= rec.blockedUntilEpochMs) {
                // Block expired — clean up and allow
                _store.remove(clientKey);
                return false;
            }
            return true;
        }
    }

    /**
     * Returns how many minutes remain in the current block (rounded up).
     * Returns 0 if not blocked.
     */
    public long getBlockRemainingMinutes(String clientKey) {
        AttemptRecord rec = _store.get(clientKey);
        if (rec == null) return 0L;
        synchronized (rec) {
            long remaining = rec.blockedUntilEpochMs - Instant.now().toEpochMilli();
            if (remaining <= 0) return 0L;
            return (long) Math.ceil(remaining / 60_000.0);
        }
    }

    /**
     * Returns how many failed attempts this client has made (before being blocked).
     */
    public int getAttemptCount(String clientKey) {
        AttemptRecord rec = _store.get(clientKey);
        if (rec == null) return 0;
        synchronized (rec) { return rec.count; }
    }

    /**
     * Builds the composite client key from IP and User-Agent.
     *
     * Format: "<ip>|<ua-fingerprint>"
     *   - IP: extracted from X-Forwarded-For (for proxy/ngrok) or remote address
     *   - UA fingerprint: first 80 chars of User-Agent header (enough to identify the browser)
     *
     * This blocks both the IP and the specific browser simultaneously,
     * without requiring persistent session state.
     */
    public static String buildClientKey(jakarta.servlet.http.HttpServletRequest request) {
        String ip = resolveClientIp(request);
        String ua = request.getHeader("User-Agent");
        String uaFragment = (ua != null && ua.length() > 80) ? ua.substring(0, 80) : (ua != null ? ua : "unknown-ua");
        return ip + "|" + uaFragment;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Extracts the real client IP, respecting reverse proxy headers.
     * Priority: X-Forwarded-For → X-Real-IP → remoteAddr (direct connection)
     */
    private static String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For can be "client, proxy1, proxy2" — take the first (original client)
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return request.getRemoteAddr();
    }
}
