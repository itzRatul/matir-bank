package com.matirbank.keyservice.controller;

import com.matirbank.keyservice.model.KeyRecord;
import com.matirbank.keyservice.repository.KeyRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/keys")
public class KeyController {

    @Autowired
    private KeyRecordRepository keyRecordRepository;

    @Value("${matir.bank.internal.secret}")
    private String internalSecret;

    private void validateSecret(String secretHeader) {
        if (secretHeader == null || !secretHeader.equals(internalSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal secret");
        }
    }

    @GetMapping("/{recordType}/{recordId}")
    public ResponseEntity<KeyRecord> getKey(
            @PathVariable String recordType,
            @PathVariable String recordId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secretHeader) {
        
        validateSecret(secretHeader);

        // Fetch or auto-generate key
        Optional<KeyRecord> existingKey = keyRecordRepository.findByRecordTypeAndRecordId(recordType, recordId);
        if (existingKey.isPresent()) {
            return ResponseEntity.ok(existingKey.get());
        }

        // Auto-generate random key
        String randomKey = UUID.randomUUID().toString().replace("-", "");
        KeyRecord newRecord = new KeyRecord(recordType, recordId, randomKey);
        KeyRecord savedRecord = keyRecordRepository.save(newRecord);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRecord);
    }

    @PostMapping
    public ResponseEntity<KeyRecord> saveKey(
            @RequestBody KeyRecord request,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secretHeader) {

        validateSecret(secretHeader);

        if (request.getRecordType() == null || request.getRecordId() == null || request.getXorKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields");
        }

        // Check if already exists
        Optional<KeyRecord> existingKey = keyRecordRepository.findByRecordTypeAndRecordId(
                request.getRecordType(), request.getRecordId());

        KeyRecord savedRecord;
        if (existingKey.isPresent()) {
            KeyRecord record = existingKey.get();
            record.setXorKey(request.getXorKey());
            savedRecord = keyRecordRepository.save(record);
        } else {
            savedRecord = keyRecordRepository.save(request);
        }

        return ResponseEntity.ok(savedRecord);
    }
}
