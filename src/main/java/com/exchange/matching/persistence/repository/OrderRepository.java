package com.exchange.matching.persistence.repository;

import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.persistence.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    long countByUserIdAndStatusIn(String userId, List<OrderStatus> statuses);

    long countByStatusIn(List<OrderStatus> statuses);

    /** Vrai si un autre utilisateur que {@code userId} a deja passe un ordre sur ce symbole. */
    boolean existsBySymbolAndUserIdNot(String symbol, String userId);

    List<OrderEntity> findBySymbolAndUserIdAndStatusIn(String symbol, String userId, List<OrderStatus> statuses);

    @Modifying
    @Query("delete from OrderEntity o where o.symbol = :symbol")
    int deleteAllBySymbol(String symbol);

    /** Utilise au demarrage pour recharger tous les ordres actifs dans le MatchingEngine en memoire. */
    List<OrderEntity> findByStatusInOrderBySequenceAsc(List<OrderStatus> statuses);

    /**
     * Verrou pessimiste sur la ligne de l'ordre: serialise l'annulation et le
     * reglement d'un trade sur le meme ordre, pour qu'un statut CANCELLED ne
     * soit jamais ecrase par un PARTIALLY_FILLED concurrent (ou l'inverse).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(String id);
}
