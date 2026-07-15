package com.matirbank.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Client that calls Space 1 (Key Service) to get/store XOR keys.
 * Authentication is done via the X-Internal-Secret header.
 */
@Service
public class KeyServiceClient {

    @Value("${matir.bank.keyservice.url}")
    private String keyServiceUrl;

    @Value("${matir.bank.keyservice.secret}")
    private String internalSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Fetch the XOR key for a record. If none exists, Space 1 auto-generates one.
     * @param recordType e.g. "user", "account", "transaction"
     * @param recordId   the entity ID as string
     * @return the xorKey string
     */
    @SuppressWarnings("unchecked")
    public String getOrCreateKey(String recordType, String recordId) {
        String url = keyServiceUrl + "/api/keys/" + recordType + "/" + recordId;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        if (response.getBody() == null) {
            throw new RuntimeException("Key service returned null body for " + recordType + "/" + recordId);
        }
        return (String) response.getBody().get("xorKey");
    }
}
