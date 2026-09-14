package com.exchange.matching.web.dto;

import com.exchange.matching.model.Order;
import com.exchange.matching.model.Trade;

import java.util.List;

public class PlaceOrderResponse {
    public OrderResponse order;
    public List<TradeResponse> trades;

    public static PlaceOrderResponse of(Order order, List<Trade> trades) {
        PlaceOrderResponse r = new PlaceOrderResponse();
        r.order = OrderResponse.from(order);
        r.trades = trades.stream().map(TradeResponse::from).toList();
        return r;
    }
}
