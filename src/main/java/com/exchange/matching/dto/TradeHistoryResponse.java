package com.exchange.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Releve d'un trader: synthese chiffree + detail de ses executions. */
public record TradeHistoryResponse(
    String username,
    Instant generatedAt,
    Summary summary,
    List<MyTradeResponse> trades
) {

    /**
     * @param netCashFlow ventes - achats: positif si le trader a encaisse plus qu'il n'a depense
     */
    public record Summary(
        int tradeCount,
        int buyCount,
        int sellCount,
        BigDecimal totalBought,
        BigDecimal totalSold,
        BigDecimal netCashFlow
    ) {}
}
