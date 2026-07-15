package com.matirbank.keyservice.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "keys_store",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"record_type", "record_id"})}
)
public class KeyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Column(name = "record_id", nullable = false)
    private String recordId;

    @Column(name = "xor_key", nullable = false)
    private String xorKey;

    public KeyRecord() {}

    public KeyRecord(String recordType, String recordId, String xorKey) {
        this.recordType = recordType;
        this.recordId = recordId;
        this.xorKey = xorKey;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getXorKey() {
        return xorKey;
    }

    public void setXorKey(String xorKey) {
        this.xorKey = xorKey;
    }
}
