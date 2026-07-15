package com.matirbank.backend.repository;

import com.matirbank.backend.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    // [HashMap] O(1) lookup by account number via DB index
    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByUserId(Long userId);

    List<Account> findByUserIdAndIsActive(Long userId, boolean isActive);
}
