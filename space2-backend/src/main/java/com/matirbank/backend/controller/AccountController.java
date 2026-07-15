package com.matirbank.backend.controller;

import com.matirbank.backend.model.Account;
import com.matirbank.backend.security.UserPrincipal;
import com.matirbank.backend.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;

    /** Get current user's own accounts */
    @GetMapping("/mine")
    public ResponseEntity<?> getMyAccounts(@AuthenticationPrincipal UserPrincipal principal) {
        List<Map<String, Object>> accounts = accountService.getAccountsForUser(principal.getUser().getId());
        return ResponseEntity.ok(accounts);
    }

    /** Get all accounts — Admin/Manager only */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    /** Open new account for a user — Admin/Manager only */
    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> openAccount(@RequestBody Map<String, String> body) {
        Long userId = Long.parseLong(body.get("userId"));
        Account.AccountType type = Account.AccountType.valueOf(body.getOrDefault("accountType", "SAVINGS"));
        Account account = accountService.openAccount(userId, type);
        return ResponseEntity.ok(Map.of(
                "message", "Account opened",
                "accountNumber", account.getAccountNumber()
        ));
    }

    /** Close an account — Admin/Manager only */
    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> closeAccount(@RequestBody Map<String, String> body) {
        String accountNumber = body.get("accountNumber");
        accountService.closeAccount(accountNumber);
        return ResponseEntity.ok(Map.of("message", "Account closed"));
    }

    /** Approve an account — Admin/Manager only */
    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> approveAccount(@RequestBody Map<String, String> body,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        String accountNumber = body.get("accountNumber");
        if (accountNumber == null || accountNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accountNumber is required"));
        }
        // Save using current staff email (obtained securely via decrypt logic or raw login/token placeholder)
        // User email is stored encrypted, but userPrincipal may contain email or id. We can use the username/ID
        String approver = "Staff_ID_" + principal.getUser().getId();
        accountService.approveAccount(accountNumber, approver);
        return ResponseEntity.ok(Map.of("message", "Account approved successfully", "approvedBy", approver));
    }

    /** Unfreeze a blocked account — Admin/Manager only */
    @PostMapping("/unfreeze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> unfreezeAccount(@RequestBody Map<String, String> body) {
        String accountNumber = body.get("accountNumber");
        if (accountNumber == null || accountNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accountNumber is required"));
        }
        accountService.unfreezeAccount(accountNumber);
        return ResponseEntity.ok(Map.of("message", "Account unfrozen successfully"));
    }

    /** Freeze an account manually — Admin/Manager only */
    @PostMapping("/freeze")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> freezeAccount(@RequestBody Map<String, String> body) {
        String accountNumber = body.get("accountNumber");
        if (accountNumber == null || accountNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "accountNumber is required"));
        }
        accountService.freezeAccount(accountNumber);
        return ResponseEntity.ok(Map.of("message", "Account frozen successfully"));
    }
}
