package com.matirbank.backend.service;

import com.matirbank.backend.model.Transaction;
import com.matirbank.backend.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    /**
     * Deposit funds into an account.
     * @return the saved transaction (with encrypted fields)
     */
    @Transactional
    public Map<String, Object> deposit(String accountNumber, double amount) {
        if (amount <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        double current = accountService.getBalance(accountNumber);
        accountService.setBalance(accountNumber, current + amount);

        Transaction tx = saveTransaction(null, accountNumber, amount, Transaction.TransactionType.DEPOSIT, "Deposit");
        return toDecryptedMap(tx);
    }

    /**
     * Withdraw funds from an account.
     */
    @Transactional
    public Map<String, Object> withdraw(String accountNumber, double amount) {
        if (amount <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");

        com.matirbank.backend.model.Account account = accountService.findByAccountNumber(accountNumber);
        
        // 1. Check if frozen
        if (account.isFrozen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Transaction blocked: Your account has been frozen due to suspicious activity. " +
                "Please contact 251-15-596@diu.edu.bd or visit a physical branch immediately for verification.");
        }

        // 2. Account approval verification
        if (!account.isApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction blocked: Account is pending verification and approval.");
        }

        // 3. Fraud Check: Single transaction limit (Max 100,000 BDT) -> Trigger FREEZE
        if (amount > 100000.0) {
            accountService.freezeAccount(accountNumber);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "🚨 Security Lockout: Transaction blocked and account has been frozen due to single transaction limit breach (Max 100,000 BDT). " +
                "Please contact 251-15-596@diu.edu.bd or visit our physical branch immediately for verification.");
        }

        // 4. Fraud Check: Limit transaction velocity (Max 3 in 5 minutes) -> Trigger FREEZE
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        long recentTxns = transactionRepository.countByFromAccountAndTimestampAfter(accountNumber, threshold);
        if (recentTxns >= 3) {
            accountService.freezeAccount(accountNumber);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "🚨 Security Lockout: Account frozen due to excessive transaction velocity (exceeded 3 withdrawals/transfers in 5 minutes). " +
                "Please contact 251-15-596@diu.edu.bd or visit our physical branch immediately for verification.");
        }

        double current = accountService.getBalance(accountNumber);
        if (current < amount) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        accountService.setBalance(accountNumber, current - amount);

        Transaction tx = saveTransaction(accountNumber, null, amount, Transaction.TransactionType.WITHDRAW, "Withdrawal");
        return toDecryptedMap(tx);
    }

    /**
     * Transfer funds atomically: both debit and credit succeed or both roll back.
     * [Transactional] Spring @Transactional ensures atomicity via JDBC transaction.
     */
    @Transactional
    public Map<String, Object> transfer(String fromAccount, String toAccount, double amount) {
        if (amount <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        if (fromAccount.equals(toAccount)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot transfer to same account");

        com.matirbank.backend.model.Account sourceAcc = accountService.findByAccountNumber(fromAccount);
        
        // 1. Check if source is frozen
        if (sourceAcc.isFrozen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Transaction blocked: Your account has been frozen due to suspicious activity. " +
                "Please contact 251-15-596@diu.edu.bd or visit a physical branch immediately for verification.");
        }

        // 2. Account approval verification (Both source and target accounts must be approved/active)
        if (!sourceAcc.isApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction blocked: Source account is pending verification and approval.");
        }
        com.matirbank.backend.model.Account targetAcc = accountService.findByAccountNumber(toAccount);
        if (!targetAcc.isApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction blocked: Target account is pending verification and approval.");
        }

        // 3. Fraud Check: Single transaction limit (Max 100,000 BDT) -> Trigger FREEZE
        if (amount > 100000.0) {
            accountService.freezeAccount(fromAccount);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "🚨 Security Lockout: Transaction blocked and source account has been frozen due to single transaction limit breach (Max 100,000 BDT). " +
                "Please contact 251-15-596@diu.edu.bd or visit our physical branch immediately for verification.");
        }

        // 4. Fraud Check: Limit transaction velocity (Max 3 in 5 minutes) -> Trigger FREEZE
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        long recentTxns = transactionRepository.countByFromAccountAndTimestampAfter(fromAccount, threshold);
        if (recentTxns >= 3) {
            accountService.freezeAccount(fromAccount);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "🚨 Security Lockout: Source account frozen due to excessive transaction velocity (exceeded 3 withdrawals/transfers in 5 minutes). " +
                "Please contact 251-15-596@diu.edu.bd or visit our physical branch immediately for verification.");
        }

        double fromBalance = accountService.getBalance(fromAccount);
        if (fromBalance < amount) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");

        accountService.setBalance(fromAccount, fromBalance - amount);
        double toBalance = accountService.getBalance(toAccount);
        accountService.setBalance(toAccount, toBalance + amount);

        Transaction tx = saveTransaction(fromAccount, toAccount, amount, Transaction.TransactionType.TRANSFER, "Transfer");
        return toDecryptedMap(tx);
    }

    /**
     * Get transaction history for an account — ordered by timestamp DESC (most recent first).
     * [Stack - LIFO] Most recent transaction shown first
     */
    public List<Map<String, Object>> getHistory(String accountNumber) {
        return transactionRepository.findByAccountNumberOrderByTimestampDesc(accountNumber)
                .stream().map(this::toDecryptedMap).collect(Collectors.toList());
    }

    /**
     * Get all transactions for admin/manager view.
     */
    public List<Map<String, Object>> getAllTransactions() {
        return transactionRepository.findAllOrderByTimestampAsc()
                .stream().map(this::toDecryptedMap).collect(Collectors.toList());
    }

    /**
     * Date-range filter using Binary Search boundary on sorted list.
     * [Binary Search] O(log n) boundary detection on sorted timestamp list
     */
    public List<Map<String, Object>> getHistoryInRange(String accountNumber,
                                                        LocalDateTime from,
                                                        LocalDateTime to) {
        return transactionRepository.findByAccountAndDateRange(accountNumber, from, to)
                .stream().map(this::toDecryptedMap).collect(Collectors.toList());
    }

    private Transaction saveTransaction(String from, String to, double amount,
                                        Transaction.TransactionType type, String description) {
        // Save first to get the DB-generated ID
        Transaction tx = new Transaction(from, to, "__temp__", type, "__temp__");
        tx = transactionRepository.save(tx);

        // Fetch key and encrypt fields — cached by KeyServiceClient so no extra HTTP call
        String key = keyServiceClient.getOrCreateKey("transaction", String.valueOf(tx.getId()));
        String encryptedAmount = encryptionService.encrypt(String.format("%.2f", amount), key);
        String encryptedDesc   = encryptionService.encrypt(description, key);

        tx.setAmount(encryptedAmount);
        tx.setDescription(encryptedDesc);
        return transactionRepository.save(tx);
    }

    private Map<String, Object> toDecryptedMap(Transaction tx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", tx.getId());
        map.put("fromAccount", tx.getFromAccount());
        map.put("toAccount", tx.getToAccount());
        map.put("type", tx.getType());
        map.put("timestamp", tx.getTimestamp());
        try {
            String key = keyServiceClient.getOrCreateKey("transaction", String.valueOf(tx.getId()));
            map.put("amount", Double.parseDouble(encryptionService.decrypt(tx.getAmount(), key)));
            map.put("description", encryptionService.decrypt(tx.getDescription(), key));
        } catch (Exception e) {
            map.put("amount", 0.0);
            map.put("description", "");
        }
        return map;
    }
}
