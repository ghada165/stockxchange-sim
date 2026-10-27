package com.exchange.matching.web.dto;

import java.math.BigDecimal;

public record OrderBookLevelDto(
    BigDecimal price,
    BigDecimal totalQuantity,
    int orderCount
) {}
