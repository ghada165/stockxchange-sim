package com.exchange.matching.web.dto;

import java.util.List;

public class OrderBookResponse {
    public String symbol;
    public List<OrderBookLevelDto> bids;
    public List<OrderBookLevelDto> asks;

    public OrderBookResponse(String symbol, List<OrderBookLevelDto> bids, List<OrderBookLevelDto> asks) {
        this.symbol = symbol;
        this.bids = bids;
        this.asks = asks;
    }
}
