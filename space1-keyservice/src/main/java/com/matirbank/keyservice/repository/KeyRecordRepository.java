package com.matirbank.keyservice.repository;

import com.matirbank.keyservice.model.KeyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface KeyRecordRepository extends JpaRepository<KeyRecord, Long> {
    // [B-Tree/Indexing] O(log N) lookup by record type and record ID using database indexes
    Optional<KeyRecord> findByRecordTypeAndRecordId(String recordType, String recordId);
}
