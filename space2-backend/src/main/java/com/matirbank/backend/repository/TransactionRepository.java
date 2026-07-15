package com.matirbank.backend.repository;

import com.matirbank.backend.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // [Stack - LIFO] Retrieve by account ordered by timestamp DESC so most recent comes first
    @Query("SELECT t FROM Transaction t WHERE (t.fromAccount = :accountNumber OR t.toAccount = :accountNumber) ORDER BY t.timestamp DESC")
    List<Transaction> findByAccountNumberOrderByTimestampDesc(@Param("accountNumber") String accountNumber);

    // [Sorted List + Binary Search] All transactions sorted - can be range-queried
    @Query("SELECT t FROM Transaction t ORDER BY t.timestamp ASC")
    List<Transaction> findAllOrderByTimestampAsc();

    // Date-range queries for admin/manager reports
    @Query("SELECT t FROM Transaction t WHERE (t.fromAccount = :accountNumber OR t.toAccount = :accountNumber) AND t.timestamp BETWEEN :from AND :to ORDER BY t.timestamp DESC")
    List<Transaction> findByAccountAndDateRange(
        @Param("accountNumber") String accountNumber,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    // Fraud detection: check transaction attempts in time frame
    long countByFromAccountAndTimestampAfter(String fromAccount, LocalDateTime threshold);
}
