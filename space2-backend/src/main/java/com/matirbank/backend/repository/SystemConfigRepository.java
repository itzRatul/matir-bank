package com.matirbank.backend.repository;

import com.matirbank.backend.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
    Optional<SystemConfig> findByConfigKey(String configKey);
}
