package com.matirbank.backend.service;

import com.matirbank.backend.model.Loan;
import com.matirbank.backend.model.SystemConfig;
import com.matirbank.backend.repository.LoanRepository;
import com.matirbank.backend.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LoanService {

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    @Value("${matir.bank.default.savings-interest-rate}")
    private double defaultInterestRate;

    /**
     * Apply for a loan.
     * [Queue - FIFO] Loans are processed in creation order
     */
    public Map<String, Object> applyForLoan(Long accountId, double principalAmount, int tenureMonths) {
        if (principalAmount <= 0 || tenureMonths <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid loan parameters");
        }

        double rate = getSavingsInterestRate();

        // Save with temp principal to get ID, then encrypt
        Loan loan = new Loan(accountId, "__temp__", rate, tenureMonths);
        loan = loanRepository.save(loan);

        String key = keyServiceClient.getOrCreateKey("loan", String.valueOf(loan.getId()));
        loan.setPrincipal(encryptionService.encrypt(String.format("%.2f", principalAmount), key));
        loan = loanRepository.save(loan);

        return toDecryptedMap(loan);
    }

    /**
     * Approve a pending loan.
     */
    public Map<String, Object> approveLoan(Long loanId) {
        Loan loan = findById(loanId);
        if (loan.getStatus() != Loan.LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan is not in PENDING status");
        }
        loan.setStatus(Loan.LoanStatus.APPROVED);
        return toDecryptedMap(loanRepository.save(loan));
    }

    /**
     * Reject a pending loan.
     */
    public Map<String, Object> rejectLoan(Long loanId) {
        Loan loan = findById(loanId);
        if (loan.getStatus() != Loan.LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loan is not in PENDING status");
        }
        loan.setStatus(Loan.LoanStatus.REJECTED);
        return toDecryptedMap(loanRepository.save(loan));
    }

    /**
     * Get all PENDING loans in FIFO order (oldest first).
     * [Queue - FIFO] First-applied, first-processed
     */
    public List<Map<String, Object>> getPendingLoans() {
        return loanRepository.findByStatusOrderByCreatedAtAsc(Loan.LoanStatus.PENDING)
                .stream().map(this::toDecryptedMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLoansForAccount(Long accountId) {
        return loanRepository.findByAccountId(accountId)
                .stream().map(this::toDecryptedMap).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAllLoans() {
        return loanRepository.findAll().stream().map(this::toDecryptedMap).collect(Collectors.toList());
    }

    private Loan findById(Long loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found: " + loanId));
    }

    private Map<String, Object> toDecryptedMap(Loan loan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", loan.getId());
        map.put("accountId", loan.getAccountId());
        map.put("interestRate", loan.getInterestRate());
        map.put("tenureMonths", loan.getTenureMonths());
        map.put("status", loan.getStatus());
        map.put("createdAt", loan.getCreatedAt());
        try {
            String key = keyServiceClient.getOrCreateKey("loan", String.valueOf(loan.getId()));
            double principal = Double.parseDouble(encryptionService.decrypt(loan.getPrincipal(), key));
            map.put("principal", principal);
            // [EMI Formula] EMI = P × r(1+r)^n / ((1+r)^n − 1)
            map.put("monthlyEmi", Loan.calculateEMI(principal, loan.getInterestRate(), loan.getTenureMonths()));
        } catch (Exception e) {
            map.put("principal", 0.0);
            map.put("monthlyEmi", 0.0);
        }
        return map;
    }

    private double getSavingsInterestRate() {
        return systemConfigRepository.findByConfigKey(SystemConfig.SAVINGS_INTEREST_RATE)
                .map(c -> Double.parseDouble(c.getConfigValue()))
                .orElse(defaultInterestRate);
    }
}
