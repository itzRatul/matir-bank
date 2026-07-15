package com.matirbank.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", unique = true, nullable = false)
    private String accountNumber; // plaintext - used for lookup

    @Column(name = "user_id", nullable = false)
    private Long userId; // plaintext - foreign key

    @Column(name = "balance")
    private String balance; // encrypted

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_approved", nullable = false)
    private boolean isApproved = false; // Requires Admin/Manager approval

    @Column(name = "is_frozen", nullable = false)
    private boolean isFrozen = false; // Frozen due to suspicious activity

    @Column(name = "approved_by")
    private String approvedBy; // Email or name of approver (Admin/Manager)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // === Enums ===
    public enum AccountType { SAVINGS, CURRENT }

    // === Constructors ===
    public Account() {}

    public Account(String accountNumber, Long userId, String balance, AccountType accountType) {
        this.accountNumber = accountNumber;
        this.userId = userId;
        this.balance = balance;
        this.accountType = accountType;
    }

    // === Getters / Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public boolean isApproved() { return isApproved; }
    public void setApproved(boolean approved) { isApproved = approved; }
    public boolean isFrozen() { return isFrozen; }
    public void setFrozen(boolean frozen) { isFrozen = frozen; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
