package com.exchange.matching.dto;

import java.util.List;

public record OrderBookResponse(
    String symbol,
    List<OrderBookLevelDto> bids,
    List<OrderBookLevelDto> asks
) {}
