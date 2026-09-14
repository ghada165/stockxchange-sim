package com.exchange.matching.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades", indexes = {
    @Index(name = "idx_trades_symbol", columnList = "symbol"),
    @Index(name = "idx_trades_buy_order", columnList = "buyOrderId"),
    @Index(name = "idx_trades_sell_order", columnList = "sellOrderId")
})
public class TradeEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String buyOrderId;

    @Column(nullable = false)
    private String sellOrderId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false)
    private Instant executedAt;

    protected TradeEntity() {
    }

    public TradeEntity(String id, String symbol, String buyOrderId, String sellOrderId,
                        BigDecimal price, BigDecimal quantity, Instant executedAt) {
        this.id = id;
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = executedAt;
    }

    public String getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public Instant getExecutedAt() { return executedAt; }
}
