package com.exchange.matching.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represente une transaction executee entre un ordre acheteur et un ordre
 * vendeur. Immutable: une fois cree, un trade ne change jamais.
 */
public final class Trade {

    private final String id;
    private final String symbol;
    private final String buyOrderId;
    private final String sellOrderId;
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final Instant executedAt;

    public Trade(String symbol, String buyOrderId, String sellOrderId,
                 BigDecimal price, BigDecimal quantity) {
        this.id = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getBuyOrderId() { return buyOrderId; }
    public String getSellOrderId() { return sellOrderId; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public Instant getExecutedAt() { return executedAt; }

    @Override
    public String toString() {
        return "Trade{symbol='%s', price=%s, qty=%s, buy=%s, sell=%s}"
            .formatted(symbol, price, quantity, buyOrderId, sellOrderId);
    }
}
