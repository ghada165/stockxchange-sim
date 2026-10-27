package com.exchange.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Statistiques d'un symbole sur une periode glissante.
 *
 * @param averagePrice prix moyen pondere par les volumes (VWAP) = montant / volume
 * @param changePercent variation du dernier prix par rapport au prix d'ouverture de la periode
 */
public record SymbolStatsResponse(
    String symbol,
    Instant since,
    long tradeCount,
    BigDecimal volume,
    BigDecimal turnover,
    BigDecimal averagePrice,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal last,
    BigDecimal changePercent
) {}
