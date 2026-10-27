package com.exchange.matching.web.dto;

import com.exchange.matching.model.Order;
import com.exchange.matching.model.Trade;

import java.util.List;

public record PlaceOrderResponse(
    OrderResponse order,
    List<TradeResponse> trades
) {

    public static PlaceOrderResponse of(Order order, List<Trade> trades) {
        return new PlaceOrderResponse(
            OrderResponse.from(order),
            trades.stream().map(TradeResponse::from).toList()
        );
    }
}
