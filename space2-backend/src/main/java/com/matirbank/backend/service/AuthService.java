package com.matirbank.backend.service;

import com.matirbank.backend.model.Account;
import com.matirbank.backend.model.SystemConfig;
import com.matirbank.backend.model.User;
import com.matirbank.backend.repository.UserRepository;
import com.matirbank.backend.repository.SystemConfigRepository;
import com.matirbank.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AccountService accountService;

    @Value("${matir.bank.default.max-customers}")
    private int defaultMaxCustomers;

    /**
     * Register a new CUSTOMER account.
     * Enforces max-customer cap from system_config.
     * @Transactional ensures the user + account creation are atomic:
     * if openAccount() fails, the user save is also rolled back.
     */
    @Transactional
    public User register(String plainName, String plainEmail, String plainPassword, String plainNid, String plainPhone, String plainAddress) {
        // Check max customer cap
        int maxCustomers = getMaxCustomers();
        long currentCount = userRepository.countByRole(User.Role.CUSTOMER);
        if (currentCount >= maxCustomers) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Registration closed: customer limit (" + maxCustomers + ") reached.");
        }

        User user = new User();
        user.setEmail("PENDING_" + System.nanoTime());
        user.setName("PENDING");
        user.setPassword(passwordEncoder.encode(plainPassword));  // [BCrypt] one-way hash
        user.setRole(User.Role.CUSTOMER);
        user = userRepository.save(user);

        // Now fetch key from Space 1 and encrypt sensitive fields
        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(user.getId()));
        user.setName(encryptionService.encrypt(plainName, key));
        user.setEmail(encryptionService.encrypt(plainEmail.toLowerCase(), key));
        user.setNid(encryptionService.encrypt(plainNid, key));
        user.setPhone(encryptionService.encrypt(plainPhone, key));
        user.setAddress(encryptionService.encrypt(plainAddress, key));
        user = userRepository.save(user);

        // Auto-create a SAVINGS account for the new customer (defaults to pending approval)
        accountService.openAccount(user.getId(), Account.AccountType.SAVINGS);

        return user;
    }

    /**
     * Login: find user whose decrypted email AND BCrypt password both match.
     * Returns JWT on success.
     *
     * Note: email is not globally unique at the ciphertext level (each user has
     * their own XOR key), so multiple accounts may share the same plaintext email.
     * We must verify the password on every email match, not stop at the first one.
     */
    public String login(String plainEmail, String plainPassword) {
        // [Linear scan] Scan users — acceptable at demo scale
        User matchedUser = userRepository.findAll().stream()
                .filter(u -> {
                    try {
                        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(u.getId()));
                        String decryptedEmail = encryptionService.decrypt(u.getEmail(), key);
                        if (!plainEmail.equalsIgnoreCase(decryptedEmail)) {
                            return false;
                        }
                        return passwordEncoder.matches(plainPassword, u.getPassword());
                    } catch (Exception e) {
                        System.err.println("[Login Error] Failed to verify user ID " + u.getId() + ": " + e.getMessage());
                        e.printStackTrace();
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        return jwtUtil.generateToken(matchedUser);
    }

    public long registerCount() {
        return userRepository.count();
    }

    private int getMaxCustomers() {
        return systemConfigRepository.findByConfigKey(SystemConfig.MAX_CUSTOMERS)
                .map(c -> Integer.parseInt(c.getConfigValue()))
                .orElse(defaultMaxCustomers);
    }
}
