package com.matirbank.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId; // plaintext

    @Column(name = "principal")
    private String principal; // encrypted

    @Column(name = "interest_rate")
    private Double interestRate; // plaintext (not PII)

    @Column(name = "tenure_months")
    private Integer tenureMonths; // plaintext

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LoanStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.status = LoanStatus.PENDING;
    }

    // === Enums ===
    public enum LoanStatus { PENDING, APPROVED, REJECTED, PAID }

    // === Constructors ===
    public Loan() {}

    public Loan(Long accountId, String principal, Double interestRate, Integer tenureMonths) {
        this.accountId = accountId;
        this.principal = principal;
        this.interestRate = interestRate;
        this.tenureMonths = tenureMonths;
    }

    // === Helpers ===

    /**
     * Calculate EMI using standard formula.
     * [EMI Formula] EMI = P × r(1+r)^n / ((1+r)^n − 1)
     * where r = monthly interest rate, n = tenure in months
     */
    public static double calculateEMI(double principal, double annualRatePercent, int tenureMonths) {
        double monthlyRate = annualRatePercent / 100.0 / 12.0;
        if (monthlyRate == 0) return principal / tenureMonths;
        double factor = Math.pow(1 + monthlyRate, tenureMonths);
        return (principal * monthlyRate * factor) / (factor - 1);
    }

    // === Getters / Setters ===
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getPrincipal() { return principal; }
    public void setPrincipal(String principal) { this.principal = principal; }
    public Double getInterestRate() { return interestRate; }
    public void setInterestRate(Double interestRate) { this.interestRate = interestRate; }
    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
