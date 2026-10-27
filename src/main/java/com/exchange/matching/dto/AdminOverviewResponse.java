package com.exchange.matching.dto;

import java.math.BigDecimal;

/** Tableau de bord de supervision. Les chiffres de trading portent sur les dernieres 24 h. */
public record AdminOverviewResponse(
    long users,
    long traders,
    long suspendedUsers,
    long symbols,
    long suspendedSymbols,
    long activeOrders,
    long tradesLast24h,
    BigDecimal volumeLast24h,
    BigDecimal turnoverLast24h,
    boolean marketOpen
) {}
