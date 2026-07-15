package com.matirbank.backend.repository;

import com.matirbank.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // Count customers for max-customer cap enforcement
    long countByRole(User.Role role);

    // NOTE: findByEmail() is intentionally omitted.
    // Emails are stored XOR-encrypted, so a plaintext email lookup
    // would never match. Email lookup is done via full-scan + decrypt
    // in AuthService and UserDetailsServiceImpl.
}
