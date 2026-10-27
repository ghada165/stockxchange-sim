package com.exchange.matching.dto;

import java.math.BigDecimal;

public record OrderBookLevelDto(
    BigDecimal price,
    BigDecimal totalQuantity,
    int orderCount
) {}
