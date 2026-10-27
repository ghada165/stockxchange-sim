package com.exchange.matching.web.dto;

import java.util.List;

public record OrderBookResponse(
    String symbol,
    List<OrderBookLevelDto> bids,
    List<OrderBookLevelDto> asks
) {}
