package com.exchange.matching.web.dto;

import com.exchange.matching.model.Trade;
import com.exchange.matching.persistence.entity.TradeEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
    String id,
    String symbol,
    BigDecimal price,
    BigDecimal quantity,
    Instant executedAt
) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
            trade.getId(),
            trade.getSymbol(),
            trade.getPrice(),
            trade.getQuantity(),
            trade.getExecutedAt()
        );
    }

    public static TradeResponse from(TradeEntity entity) {
        return new TradeResponse(
            entity.getId(),
            entity.getSymbol(),
            entity.getPrice(),
            entity.getQuantity(),
            entity.getExecutedAt()
        );
    }
}
