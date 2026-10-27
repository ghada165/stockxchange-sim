package com.exchange.matching.persistence.entity;

import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projection persistee d'un {@link com.exchange.matching.model.Order}.
 *
 * Le MatchingEngine reste la source de verite EN MEMOIRE pendant que
 * l'application tourne (c'est lui qui execute le matching, pas la DB).
 * Cette entite sert a: (1) l'historique consultable, (2) recharger le
 * carnet en memoire au demarrage de l'application (voir OrderBookLoader).
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_symbol_status", columnList = "symbol,status"),
    @Index(name = "idx_orders_user", columnList = "userId")
})
public class OrderEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    /**
     * Prix unitaire qui a servi a BLOQUER les fonds/actions de l'ordre:
     * le prix limite pour un LIMIT, le prix plafond (ou plancher) pour un
     * MARKET. Toute liberation de reservation (trade, annulation, reliquat
     * MARKET) se fait sur cette base, jamais sur le prix d'execution: c'est
     * ce qui garde le lockedBalance du wallet exact au centime pres.
     */
    @Column(precision = 19, scale = 4)
    private BigDecimal reservationPrice;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private long sequence;

    protected OrderEntity() {
        // requis par JPA
    }

    public OrderEntity(String id, String userId, String symbol, OrderSide side, OrderType type,
                        BigDecimal price, BigDecimal reservationPrice, BigDecimal quantity,
                        BigDecimal remainingQuantity, OrderStatus status, Instant createdAt, long sequence) {
        this.id = id;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.reservationPrice = reservationPrice;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.createdAt = createdAt;
        this.sequence = sequence;
    }

    // --- Getters / Setters (necessaires a JPA + mise a jour post-matching) ---

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public OrderType getType() { return type; }
    public BigDecimal getPrice() { return price; }

    /** Prix de reservation; repli sur le prix limite pour les lignes anterieures a cette colonne. */
    public BigDecimal getReservationPrice() { return reservationPrice != null ? reservationPrice : price; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(BigDecimal remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
}
