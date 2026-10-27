package com.exchange.matching.dto;

import com.exchange.matching.model.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

/** Une execution vue par l'utilisateur: achat ou vente de SON point de vue. */
public record MyTradeResponse(
    String id,
    String symbol,
    OrderSide side,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal amount,
    Instant executedAt
) {}
