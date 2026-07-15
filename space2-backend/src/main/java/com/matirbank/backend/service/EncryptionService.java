package com.matirbank.backend.service;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * XOR-based encryption/decryption of sensitive fields.
 *
 * [XOR Cipher] O(n) in data length:
 *   encrypt: ciphertext[i] = plaintext[i] XOR key[i % key.length]
 *   decrypt: plaintext[i]  = ciphertext[i] XOR key[i % key.length]
 *
 * The key is retrieved from Space 1 (Key Service) via KeyServiceClient.
 * Result is Base64-encoded so it can be safely stored as TEXT in SQLite.
 */
@Service
public class EncryptionService {

    /**
     * Encrypt a plaintext string using the given XOR key.
     * @param plaintext the sensitive field value (e.g. a name, email, balance)
     * @param key       the XOR key fetched from Space 1
     * @return Base64-encoded ciphertext
     */
    public String encrypt(String plaintext, String key) {
        if (plaintext == null) return null;
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertextBytes = new byte[plaintextBytes.length];

        // [XOR Cipher] O(n) loop — key wraps around using modulo
        for (int i = 0; i < plaintextBytes.length; i++) {
            ciphertextBytes[i] = (byte) (plaintextBytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.getEncoder().encodeToString(ciphertextBytes);
    }

    /**
     * Decrypt a Base64-encoded ciphertext using the given XOR key.
     * XOR is its own inverse: plaintext = ciphertext XOR key.
     * @param ciphertext Base64-encoded ciphertext from the DB
     * @param key        the XOR key fetched from Space 1
     * @return decrypted plaintext string
     */
    public String decrypt(String ciphertext, String key) {
        if (ciphertext == null) return null;
        byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] plaintextBytes = new byte[ciphertextBytes.length];

        // [XOR Cipher] Decryption is identical to encryption — XOR is self-inverse
        for (int i = 0; i < ciphertextBytes.length; i++) {
            plaintextBytes[i] = (byte) (ciphertextBytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return new String(plaintextBytes, StandardCharsets.UTF_8);
    }
}
