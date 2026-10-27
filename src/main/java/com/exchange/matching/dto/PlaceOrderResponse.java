package com.exchange.matching.dto;

import com.exchange.matching.engine.MatchResult;
import com.exchange.matching.model.Order;

import java.util.List;

public record PlaceOrderResponse(
    OrderResponse order,
    List<TradeResponse> trades
) {

    /** L'etat renvoye est la photo prise par le moteur juste apres le matching. */
    public static PlaceOrderResponse of(Order order, MatchResult result) {
        OrderResponse snapshot = new OrderResponse(
            order.getId(),
            order.getSymbol(),
            order.getSide(),
            order.getType(),
            order.getPrice(),
            order.getQuantity(),
            result.remainingQuantity(),
            result.status(),
            order.getCreatedAt()
        );
        return new PlaceOrderResponse(snapshot, result.trades().stream().map(TradeResponse::from).toList());
    }
}
