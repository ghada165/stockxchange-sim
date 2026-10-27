package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.MarketSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSettingsRepository extends JpaRepository<MarketSettingsEntity, Integer> {
}
