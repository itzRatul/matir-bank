package com.matirbank.backend.repository;

import com.matirbank.backend.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    // [Queue - FIFO] Retrieve by status ordered by createdAt ASC for first-applied first-processed
    List<Loan> findByStatusOrderByCreatedAtAsc(Loan.LoanStatus status);

    List<Loan> findByAccountId(Long accountId);
}
