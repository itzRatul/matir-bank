package com.matirbank.backend.service;

import com.matirbank.backend.model.User;
import com.matirbank.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Map<String, Object> getUserDecrypted(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toDecryptedMap(user);
    }

    public List<Map<String, Object>> getAllCustomers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.CUSTOMER)
                .map(this::toDecryptedMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAllAdmins() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ADMIN)
                .map(this::toDecryptedMap)
                .collect(Collectors.toList());
    }

    /**
     * Create an Admin account — Manager only.
     * Uses a unique placeholder to avoid unique constraint collisions during
     * the two-step save (save first to get ID, then encrypt and re-save).
     */
    public Map<String, Object> createAdmin(String plainName, String plainEmail, String plainPassword) {
        User user = new User();
        user.setEmail("PENDING_" + System.nanoTime());
        user.setName("PENDING");
        user.setPassword(passwordEncoder.encode(plainPassword));  // [BCrypt]
        user.setRole(User.Role.ADMIN);
        user = userRepository.save(user);

        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(user.getId()));
        user.setName(encryptionService.encrypt(plainName, key));
        user.setEmail(encryptionService.encrypt(plainEmail.toLowerCase(), key));
        user = userRepository.save(user);
        return toDecryptedMap(user);
    }

    /**
     * Delete an Admin — Manager only.
     */
    public void deleteAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (user.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an Admin");
        }
        userRepository.delete(user);
    }

    private Map<String, Object> toDecryptedMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("role", user.getRole());
        map.put("createdAt", user.getCreatedAt());
        try {
            String key = keyServiceClient.getOrCreateKey("user", String.valueOf(user.getId()));
            map.put("name", encryptionService.decrypt(user.getName(), key));
            map.put("email", encryptionService.decrypt(user.getEmail(), key));
            map.put("nid", user.getNid() != null ? encryptionService.decrypt(user.getNid(), key) : "");
            map.put("phone", user.getPhone() != null ? encryptionService.decrypt(user.getPhone(), key) : "");
            map.put("address", user.getAddress() != null ? encryptionService.decrypt(user.getAddress(), key) : "");
        } catch (Exception e) {
            map.put("name", "[encrypted]");
            map.put("email", "[encrypted]");
            map.put("nid", "[encrypted]");
            map.put("phone", "[encrypted]");
            map.put("address", "[encrypted]");
        }
        return map;
    }
}
