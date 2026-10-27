package com.exchange.matching.web.dto;

import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.persistence.entity.OrderEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
    String id,
    String symbol,
    OrderSide side,
    OrderType type,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal remainingQuantity,
    OrderStatus status,
    Instant createdAt
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getSymbol(),
            order.getSide(),
            order.getType(),
            order.getPrice(),
            order.getQuantity(),
            order.getRemainingQuantity(),
            order.getStatus(),
            order.getCreatedAt()
        );
    }

    public static OrderResponse from(OrderEntity entity) {
        return new OrderResponse(
            entity.getId(),
            entity.getSymbol(),
            entity.getSide(),
            entity.getType(),
            entity.getPrice(),
            entity.getQuantity(),
            entity.getRemainingQuantity(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }
}
