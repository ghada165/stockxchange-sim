package com.exchange.matching.dto;

import com.exchange.matching.persistence.entity.PositionEntity;

import java.math.BigDecimal;

public record PositionResponse(
    String symbol,
    BigDecimal quantity,
    BigDecimal lockedQuantity,
    BigDecimal availableQuantity
) {

    public static PositionResponse from(PositionEntity position) {
        return new PositionResponse(
            position.getSymbol(),
            position.getQuantity(),
            position.getLockedQuantity(),
            position.availableQuantity()
        );
    }
}
