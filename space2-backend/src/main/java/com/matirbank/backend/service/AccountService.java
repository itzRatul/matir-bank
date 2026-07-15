package com.matirbank.backend.service;

import com.matirbank.backend.model.Account;
import com.matirbank.backend.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    /**
     * Open a new bank account for a user.
     * Generates a unique 10-digit account number.
     * @return the created Account (balance stored encrypted)
     */
    public Account openAccount(Long userId, Account.AccountType accountType) {
        String accountNumber = generateUniqueAccountNumber();
        String key = keyServiceClient.getOrCreateKey("account", accountNumber);
        String encryptedBalance = encryptionService.encrypt("0.00", key);

        Account account = new Account(accountNumber, userId, encryptedBalance, accountType);
        account.setApproved(false); // Pending Admin/Manager approval by default
        return accountRepository.save(account);
    }

    /**
     * Approve a pending account (Admin or Manager action).
     */
    public Account approveAccount(String accountNumber, String approverEmail) {
        Account account = findByAccountNumber(accountNumber);
        account.setApproved(true);
        account.setApprovedBy(approverEmail);
        account.setActive(true);
        return accountRepository.save(account);
    }

    /**
     * Freeze an account due to suspicious activity.
     */
    public Account freezeAccount(String accountNumber) {
        Account account = findByAccountNumber(accountNumber);
        account.setFrozen(true);
        return accountRepository.save(account);
    }

    /**
     * Unfreeze a blocked account (Admin or Manager action).
     */
    public Account unfreezeAccount(String accountNumber) {
        Account account = findByAccountNumber(accountNumber);
        account.setFrozen(false);
        return accountRepository.save(account);
    }

    /**
     * Close (deactivate) an account.
     */
    public Account closeAccount(String accountNumber) {
        Account account = findByAccountNumber(accountNumber);
        account.setActive(false);
        return accountRepository.save(account);
    }

    /**
     * Get decrypted balance for an account.
     * [HashMap] O(1) lookup by account_number via DB index
     */
    public double getBalance(String accountNumber) {
        Account account = findByAccountNumber(accountNumber);
        String key = keyServiceClient.getOrCreateKey("account", accountNumber);
        return Double.parseDouble(encryptionService.decrypt(account.getBalance(), key));
    }

    /**
     * Internal: update encrypted balance in DB.
     */
    public void setBalance(String accountNumber, double newBalance) {
        Account account = findByAccountNumber(accountNumber);
        String key = keyServiceClient.getOrCreateKey("account", accountNumber);
        account.setBalance(encryptionService.encrypt(String.format("%.2f", newBalance), key));
        accountRepository.save(account);
    }

    /**
     * Get all accounts for a user with decrypted balances.
     */
    public List<Map<String, Object>> getAccountsForUser(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toDecryptedMap)
                .collect(Collectors.toList());
    }

    /**
     * Get all accounts (admin/manager view).
     */
    public List<Map<String, Object>> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toDecryptedMap)
                .collect(Collectors.toList());
    }

    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Account not found: " + accountNumber));
    }

    private Map<String, Object> toDecryptedMap(Account account) {
        // [HashMap] O(1) key-value map for JSON response
        Map<String, Object> map = new HashMap<>();
        map.put("id", account.getId());
        map.put("accountNumber", account.getAccountNumber());
        map.put("userId", account.getUserId());
        map.put("accountType", account.getAccountType());
        map.put("isActive", account.isActive());
        map.put("isApproved", account.isApproved());
        map.put("isFrozen", account.isFrozen());
        map.put("approvedBy", account.getApprovedBy());
        map.put("createdAt", account.getCreatedAt());
        try {
            String key = keyServiceClient.getOrCreateKey("account", account.getAccountNumber());
            map.put("balance", Double.parseDouble(encryptionService.decrypt(account.getBalance(), key)));
        } catch (Exception e) {
            map.put("balance", 0.0);
        }
        return map;
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            // 10-digit number starting with 10 (BDT bank style)
            accountNumber = "10" + String.format("%08d", (int) (Math.random() * 100_000_000));
        } while (accountRepository.findByAccountNumber(accountNumber).isPresent());
        return accountNumber;
    }
}
