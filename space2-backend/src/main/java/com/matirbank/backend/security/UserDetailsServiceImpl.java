package com.matirbank.backend.security;

import com.matirbank.backend.model.User;
import com.matirbank.backend.repository.UserRepository;
import com.matirbank.backend.service.EncryptionService;
import com.matirbank.backend.service.KeyServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security UserDetailsService.
 * Loads the user by their encrypted email, decrypting it via KeyServiceClient.
 *
 * NOTE: Because emails are stored encrypted, we need to find user by ID or
 * compare decrypted emails. For login, we find by ID from the JWT; for initial
 * authentication we scan and compare (only feasible at small user counts in demo).
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private KeyServiceClient keyServiceClient;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Scan users and compare decrypted emails
        // [Linear scan] - acceptable for demo scale; encrypted email prevents indexed lookup
        return userRepository.findAll().stream()
                .filter(u -> {
                    try {
                        String key = keyServiceClient.getOrCreateKey("user", String.valueOf(u.getId()));
                        return email.equalsIgnoreCase(encryptionService.decrypt(u.getEmail(), key));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return new UserPrincipal(user);
    }
}
