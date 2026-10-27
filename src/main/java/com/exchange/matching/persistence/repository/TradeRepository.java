package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.reporting.TradeStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<TradeEntity, String> {

    List<TradeEntity> findTop50BySymbolOrderByExecutedAtDesc(String symbol);

    Optional<TradeEntity> findTopBySymbolOrderByExecutedAtDesc(String symbol);

    /** Premier trade de la periode: prix d'ouverture des statistiques. */
    Optional<TradeEntity> findFirstBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(String symbol, Instant since);

    List<TradeEntity> findByBuyOrderIdOrSellOrderId(String buyOrderId, String sellOrderId);

    /** Historique par symbole sur une periode, trie pour construire les chandeliers. */
    List<TradeEntity> findBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(String symbol, Instant since);

    /** Historique par utilisateur (acheteur ou vendeur), du plus recent au plus ancien. */
    List<TradeEntity> findTop500ByBuyerIdOrSellerIdOrderByExecutedAtDesc(String buyerId, String sellerId);

    @Query("""
        select new com.exchange.matching.reporting.TradeStats(
            count(t), sum(t.quantity), sum(t.price * t.quantity), max(t.price), min(t.price))
        from TradeEntity t
        where t.symbol = :symbol and t.executedAt >= :since
        """)
    TradeStats statsForSymbol(String symbol, Instant since);

    @Query("""
        select new com.exchange.matching.reporting.TradeStats(
            count(t), sum(t.quantity), sum(t.price * t.quantity), max(t.price), min(t.price))
        from TradeEntity t
        where t.executedAt >= :since
        """)
    TradeStats statsForAll(Instant since);
}
