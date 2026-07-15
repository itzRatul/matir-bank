package com.matirbank.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "config_key")
    private String configKey; // e.g. 'MAX_CUSTOMERS', 'SAVINGS_INTEREST_RATE'

    @Column(name = "config_value")
    private String configValue;

    @Column(name = "updated_by")
    private Long updatedBy; // manager user id

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // === Constructors ===
    public SystemConfig() {}

    public SystemConfig(String configKey, String configValue, Long updatedBy) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.updatedBy = updatedBy;
    }

    // === Common config keys ===
    public static final String MAX_CUSTOMERS = "MAX_CUSTOMERS";
    public static final String SAVINGS_INTEREST_RATE = "SAVINGS_INTEREST_RATE";
    public static final String LOAN_INTEREST_RATE = "LOAN_INTEREST_RATE";
    public static final String MIN_BALANCE = "MIN_BALANCE";

    // === Getters / Setters ===
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
