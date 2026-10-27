package com.exchange.matching.dto;

import com.exchange.matching.model.AccountStatus;

import java.math.BigDecimal;

/**
 * Resume d'un symbole cote: nom, statut, prix de reference, dernier prix
 * execute, meilleurs prix achat/vente. lastPrice, bestBid et bestAsk peuvent
 * etre null (pas encore de trade, carnet vide d'un cote).
 */
public record MarketSummaryResponse(
    String symbol,
    String name,
    AccountStatus status,
    BigDecimal referencePrice,
    BigDecimal lastPrice,
    BigDecimal bestBid,
    BigDecimal bestAsk
) {}
