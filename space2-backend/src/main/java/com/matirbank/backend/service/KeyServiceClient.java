package com.matirbank.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client that calls Space 1 (Key Service) to get/store XOR keys.
 * Authentication is done via the X-Internal-Secret header.
 *
 * [Cache] In-memory ConcurrentHashMap caches keys after first fetch.
 * Keys never change once created, so caching is safe and avoids
 * repeated HTTP round-trips to the Key Service on every operation.
 * This significantly speeds up login, transfer, and all crypto ops.
 */
@Service
public class KeyServiceClient {

    @Value("${matir.bank.keyservice.url}")
    private String keyServiceUrl;

    @Value("${matir.bank.keyservice.secret}")
    private String internalSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    // Simple in-memory cache: "type/id" → xorKey
    private final ConcurrentHashMap<String, String> keyCache = new ConcurrentHashMap<>();

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Fetch the XOR key for a record. If none exists, Space 1 auto-generates one.
     * Result is cached in-memory so repeated calls for the same key skip the HTTP round-trip.
     *
     * @param recordType e.g. "user", "account", "transaction"
     * @param recordId   the entity ID as string
     * @return the xorKey string
     */
    @SuppressWarnings("unchecked")
    public String getOrCreateKey(String recordType, String recordId) {
        String cacheKey = recordType + "/" + recordId;

        // Return from cache if available (no HTTP call needed)
        String cached = keyCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Fetch from Key Service
        String url = keyServiceUrl + "/api/keys/" + recordType + "/" + recordId;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        if (response.getBody() == null) {
            throw new RuntimeException("Key service returned null body for " + recordType + "/" + recordId);
        }
        String key = (String) response.getBody().get("xorKey");

        // Store in cache for future calls
        keyCache.put(cacheKey, key);
        return key;
    }
}
