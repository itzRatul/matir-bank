package com.matirbank.backend.controller;

import com.matirbank.backend.security.UserPrincipal;
import com.matirbank.backend.service.AccountService;
import com.matirbank.backend.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    @Autowired
    private LoanService loanService;

    @Autowired
    private AccountService accountService;

    /** Customer applies for a loan on their account */
    @PostMapping("/apply")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> apply(@RequestBody Map<String, Object> body,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        String accountNumber = (String) body.get("accountNumber");
        double amount = Double.parseDouble(body.get("amount").toString());
        int tenure = Integer.parseInt(body.get("tenureMonths").toString());

        // Verify account belongs to the customer
        var account = accountService.findByAccountNumber(accountNumber);
        if (!account.getUserId().equals(principal.getUser().getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Account does not belong to you"));
        }

        return ResponseEntity.ok(loanService.applyForLoan(account.getId(), amount, tenure));
    }

    /** Get all loans for own accounts */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getMyLoans(@AuthenticationPrincipal UserPrincipal principal) {
        List<Map<String, Object>> accounts = accountService.getAccountsForUser(principal.getUser().getId());
        List<Map<String, Object>> allLoans = new java.util.ArrayList<>();
        for (Map<String, Object> acc : accounts) {
            allLoans.addAll(loanService.getLoansForAccount((Long) acc.get("id")));
        }
        return ResponseEntity.ok(allLoans);
    }

    /** Get all PENDING loans — Admin/Manager */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getPendingLoans() {
        return ResponseEntity.ok(loanService.getPendingLoans());
    }

    /** Get all loans — Admin/Manager */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getAllLoans() {
        return ResponseEntity.ok(loanService.getAllLoans());
    }

    /** Approve a loan — Admin/Manager */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.approveLoan(id));
    }

    /** Reject a loan — Admin/Manager */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.rejectLoan(id));
    }
}
