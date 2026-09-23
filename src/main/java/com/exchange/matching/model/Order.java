package com.exchange.matching.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represente un ordre d'achat ou de vente sur un symbole donne.
 *
 * IMPORTANT sur la concurrence: cette classe N'EST PAS thread-safe par elle-meme.
 * Toute mutation (fill partiel, annulation) doit se faire a l'interieur de la
 * section critique verrouillee par symbole dans {@link com.exchange.matching.engine.MatchingEngine}.
 * Ne jamais muter un Order depuis l'exterieur du moteur.
 */
public class Order {

    private final String id;
    private final String userId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType type;

    /** Null pour un ordre MARKET. */
    private final BigDecimal price;

    private final BigDecimal quantity;
    private BigDecimal remainingQuantity;
    private OrderStatus status;

    private final Instant createdAt;

    /**
     * Numero de sequence monotone attribue par l'engine a la reception.
     * Sert de tie-breaker fiable pour la time-priority (plus fiable que
     * comparer des timestamps qui peuvent avoir la meme valeur en nanosecondes
     * sous forte charge).
     */
    private long sequence;

    public Order(String userId, String symbol, OrderSide side, OrderType type,
                 BigDecimal price, BigDecimal quantity) {
        this(UUID.randomUUID().toString(), userId, symbol, side, type, price, quantity,
            quantity, OrderStatus.OPEN, Instant.now(), 0L);
    }

    /**
     * Reconstruit un Order avec un etat explicite (id, quantite restante,
     * statut, sequence d'origine). Reserve au rechargement du carnet au
     * demarrage depuis la persistance (voir OrderBookLoader) - NE PAS
     * utiliser pour creer un nouvel ordre utilisateur, qui doit toujours
     * passer par le constructeur public a 6 arguments (id genere, etat
     * initial OPEN).
     */
    public static Order reconstruct(String id, String userId, String symbol, OrderSide side,
                                     OrderType type, BigDecimal price, BigDecimal quantity,
                                     BigDecimal remainingQuantity, OrderStatus status,
                                     Instant createdAt, long sequence) {
        return new Order(id, userId, symbol, side, type, price, quantity,
            remainingQuantity, status, createdAt, sequence);
    }

    private Order(String id, String userId, String symbol, OrderSide side, OrderType type,
                  BigDecimal price, BigDecimal quantity, BigDecimal remainingQuantity,
                  OrderStatus status, Instant createdAt, long sequence) {
        if (type == OrderType.LIMIT && price == null) {
            throw new IllegalArgumentException("Un ordre LIMIT doit avoir un prix");
        }
        if (type == OrderType.MARKET && price != null) {
            throw new IllegalArgumentException("Un ordre MARKET ne doit pas avoir de prix");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La quantite doit etre strictement positive");
        }

        this.id = id;
        this.userId = Objects.requireNonNull(userId, "userId requis");
        this.symbol = Objects.requireNonNull(symbol, "symbol requis");
        this.side = Objects.requireNonNull(side, "side requis");
        this.type = Objects.requireNonNull(type, "type requis");
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.createdAt = createdAt;
        this.sequence = sequence;
    }

    // --- Mutations internes (appelees uniquement par le MatchingEngine sous lock) ---

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public void reduceRemaining(BigDecimal filledQuantity) {
        if (filledQuantity.compareTo(remainingQuantity) > 0) {
            throw new IllegalStateException(
                "Impossible de remplir plus que la quantite restante: demande=" + filledQuantity
                + " restant=" + remainingQuantity);
        }
        this.remainingQuantity = this.remainingQuantity.subtract(filledQuantity);
        if (this.remainingQuantity.signum() == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        if (this.status == OrderStatus.FILLED) {
            throw new IllegalStateException("Impossible d'annuler un ordre deja rempli");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void reject() {
        this.status = OrderStatus.REJECTED;
    }

    public boolean isActive() {
        return status == OrderStatus.OPEN || status == OrderStatus.PARTIALLY_FILLED;
    }

    // --- Getters ---

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public OrderType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public long getSequence() { return sequence; }

    @Override
    public String toString() {
        return "Order{id='%s', symbol='%s', side=%s, type=%s, price=%s, remaining=%s, status=%s, seq=%d}"
            .formatted(id, symbol, side, type, price, remainingQuantity, status, sequence);
    }
}
