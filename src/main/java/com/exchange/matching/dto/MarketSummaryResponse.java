package com.exchange.matching.dto;

import java.math.BigDecimal;

/**
 * Resume d'un symbole cote: prix de reference, dernier prix execute,
 * meilleurs prix achat/vente. Les champs peuvent etre null (pas encore de
 * trade, carnet vide d'un cote).
 */
public record MarketSummaryResponse(
    String symbol,
    BigDecimal referencePrice,
    BigDecimal lastPrice,
    BigDecimal bestBid,
    BigDecimal bestAsk
) {}
