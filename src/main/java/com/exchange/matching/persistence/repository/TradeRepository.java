package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<TradeEntity, String> {

    List<TradeEntity> findTop50BySymbolOrderByExecutedAtDesc(String symbol);

    Optional<TradeEntity> findTopBySymbolOrderByExecutedAtDesc(String symbol);

    List<TradeEntity> findByBuyOrderIdOrSellOrderId(String buyOrderId, String sellOrderId);
}
