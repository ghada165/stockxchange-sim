package com.exchange.matching.dto;

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
            trade.id(),
            trade.symbol(),
            trade.price(),
            trade.quantity(),
            trade.executedAt()
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
