package com.exchange.matching.persistence.repository;

import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<OrderEntity> findBySymbolAndStatusInOrderBySequenceAsc(String symbol, List<OrderStatus> statuses);

    /** Utilise au demarrage pour recharger tous les ordres actifs dans le MatchingEngine en memoire. */
    List<OrderEntity> findByStatusInOrderBySequenceAsc(List<OrderStatus> statuses);
}
