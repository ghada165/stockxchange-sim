package com.exchange.matching.web.dto;

import com.exchange.matching.model.Trade;

import java.math.BigDecimal;
import java.time.Instant;

public class TradeResponse {
    public String id;
    public String symbol;
    public BigDecimal price;
    public BigDecimal quantity;
    public Instant executedAt;

    public static TradeResponse from(Trade trade) {
        TradeResponse r = new TradeResponse();
        r.id = trade.getId();
        r.symbol = trade.getSymbol();
        r.price = trade.getPrice();
        r.quantity = trade.getQuantity();
        r.executedAt = trade.getExecutedAt();
        return r;
    }
}
