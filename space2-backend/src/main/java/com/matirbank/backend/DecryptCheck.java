package com.matirbank.backend;

import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.Base64;

public class DecryptCheck {
    public static void main(String[] args) {
        String keyServiceUrl = "http://localhost:8081";
        String internalSecret = "MatirBankSuperSecretSharedKey123!";
        
        RestTemplate restTemplate = new RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
        
        String url = keyServiceUrl + "/api/keys/user/2";
        org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, Map.class);
        String key = (String) response.getBody().get("xorKey");
        System.out.println("Key from KeyService: " + key);
        
        String dbEmail = "VFYJGgdTGgIOUiMCXUIaUVwTH1BW";
        
        byte[] ciphertextBytes = Base64.getDecoder().decode(dbEmail);
        byte[] keyBytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] plaintextBytes = new byte[ciphertextBytes.length];
        for (int i = 0; i < ciphertextBytes.length; i++) {
            plaintextBytes[i] = (byte) (ciphertextBytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        String decryptedEmail = new String(plaintextBytes, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Decrypted Email: " + decryptedEmail);
    }
}
