package com.exchange.matching.reporting;

import java.math.BigDecimal;

/**
 * Agregats bruts calcules par la base sur une periode (requete JPQL avec
 * expression constructeur "select new ...TradeStats(...)" dans TradeRepository).
 * Le constructeur compact remplace les null (aucun trade) par zero.
 */
public record TradeStats(
    Long tradeCount,
    BigDecimal volume,
    BigDecimal turnover,
    BigDecimal high,
    BigDecimal low
) {

    public TradeStats {
        if (tradeCount == null) tradeCount = 0L;
        if (volume == null) volume = BigDecimal.ZERO;
        if (turnover == null) turnover = BigDecimal.ZERO;
    }
}
