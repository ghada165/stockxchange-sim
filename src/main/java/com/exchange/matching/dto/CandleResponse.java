package com.exchange.matching.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Chandelier OHLC: prix d'ouverture, plus haut, plus bas, cloture et volume sur un intervalle. */
public record CandleResponse(
    Instant start,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal volume
) {}
