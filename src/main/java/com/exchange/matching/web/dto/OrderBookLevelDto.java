package com.exchange.matching.web.dto;

import java.math.BigDecimal;

public class OrderBookLevelDto {
    public BigDecimal price;
    public BigDecimal totalQuantity;
    public int orderCount;

    public OrderBookLevelDto(BigDecimal price, BigDecimal totalQuantity, int orderCount) {
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.orderCount = orderCount;
    }
}
