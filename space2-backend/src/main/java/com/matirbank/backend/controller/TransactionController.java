package com.matirbank.backend.controller;

import com.matirbank.backend.model.Account;
import com.matirbank.backend.model.User;
import com.matirbank.backend.security.UserPrincipal;
import com.matirbank.backend.service.AccountService;
import com.matirbank.backend.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    /**
     * Deposit into an account.
     * CUSTOMER can only deposit to their own account; ADMIN/MANAGER can deposit to any.
     */
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> body,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        String accountNumber = (String) body.get("accountNumber");
        double amount = Double.parseDouble(body.get("amount").toString());

        // [Security] Customers can only deposit to their own accounts
        verifyOwnershipIfCustomer(accountNumber, principal);

        return ResponseEntity.ok(transactionService.deposit(accountNumber, amount));
    }

    /**
     * Withdraw from an account.
     * CUSTOMER can only withdraw from their own account; ADMIN/MANAGER can withdraw from any.
     */
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        String accountNumber = (String) body.get("accountNumber");
        double amount = Double.parseDouble(body.get("amount").toString());

        // [Security] Customers can only withdraw from their own accounts
        verifyOwnershipIfCustomer(accountNumber, principal);

        return ResponseEntity.ok(transactionService.withdraw(accountNumber, amount));
    }

    /**
     * Transfer from one account to another.
     * CUSTOMER can only transfer FROM their own account.
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        String fromAccount = (String) body.get("fromAccount");
        String toAccount = (String) body.get("toAccount");
        double amount = Double.parseDouble(body.get("amount").toString());

        // [Security] Customers can only transfer from their own accounts
        verifyOwnershipIfCustomer(fromAccount, principal);

        return ResponseEntity.ok(transactionService.transfer(fromAccount, toAccount, amount));
    }

    /**
     * Get transaction history for a specific account.
     * CUSTOMER can only view their own account history; ADMIN/MANAGER can view any.
     */
    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<?> getHistory(@PathVariable String accountNumber,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        // [Security] Customers can only view their own account history
        verifyOwnershipIfCustomer(accountNumber, principal);

        return ResponseEntity.ok(transactionService.getHistory(accountNumber));
    }

    /** Get ALL transactions — Admin/Manager only */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    /**
     * Verifies that if the caller is a CUSTOMER, the account belongs to them.
     * ADMIN/MANAGER are unrestricted.
     */
    private void verifyOwnershipIfCustomer(String accountNumber, UserPrincipal principal) {
        User.Role role = principal.getUser().getRole();
        if (role == User.Role.CUSTOMER) {
            Account account = accountService.findByAccountNumber(accountNumber);
            if (!account.getUserId().equals(principal.getUser().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: account does not belong to you");
            }
        }
    }
}
