package com.exchange.matching.web.dto;

import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderResponse {
    public String id;
    public String symbol;
    public OrderSide side;
    public OrderType type;
    public BigDecimal price;
    public BigDecimal quantity;
    public BigDecimal remainingQuantity;
    public OrderStatus status;
    public Instant createdAt;

    public static OrderResponse from(Order order) {
        OrderResponse r = new OrderResponse();
        r.id = order.getId();
        r.symbol = order.getSymbol();
        r.side = order.getSide();
        r.type = order.getType();
        r.price = order.getPrice();
        r.quantity = order.getQuantity();
        r.remainingQuantity = order.getRemainingQuantity();
        r.status = order.getStatus();
        r.createdAt = order.getCreatedAt();
        return r;
    }

    public static OrderResponse from(com.exchange.matching.persistence.entity.OrderEntity entity) {
        OrderResponse r = new OrderResponse();
        r.id = entity.getId();
        r.symbol = entity.getSymbol();
        r.side = entity.getSide();
        r.type = entity.getType();
        r.price = entity.getPrice();
        r.quantity = entity.getQuantity();
        r.remainingQuantity = entity.getRemainingQuantity();
        r.status = entity.getStatus();
        r.createdAt = entity.getCreatedAt();
        return r;
    }
}
