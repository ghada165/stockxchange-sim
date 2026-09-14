package com.exchange.matching.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Position d'un utilisateur sur un symbole (combien d'actions il detient).
 * Meme logique de blocage que WalletEntity, mais sur la quantite d'actions
 * plutot que sur le cash: un ordre SELL LIMIT bloque `quantity` actions
 * jusqu'a execution ou annulation, pour empecher de vendre deux fois le
 * meme titre avec deux ordres simultanes.
 */
@Entity
@Table(name = "positions", uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "symbol"}))
public class PositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal lockedQuantity = BigDecimal.ZERO;

    @Version
    private long version;

    protected PositionEntity() {
    }

    public PositionEntity(String userId, String symbol) {
        this.userId = userId;
        this.symbol = symbol;
    }

    public BigDecimal availableQuantity() {
        return quantity.subtract(lockedQuantity);
    }

    public void lock(BigDecimal amount) {
        if (amount.compareTo(availableQuantity()) > 0) {
            throw new IllegalStateException("Quantite insuffisante disponible pour " + symbol);
        }
        this.lockedQuantity = this.lockedQuantity.add(amount);
    }

    public void unlock(BigDecimal amount) {
        this.lockedQuantity = this.lockedQuantity.subtract(amount);
    }

    /** Debit definitif suite a une vente executee. */
    public void settleDebit(BigDecimal amount) {
        this.quantity = this.quantity.subtract(amount);
        this.lockedQuantity = this.lockedQuantity.subtract(amount);
    }

    /** Credit suite a un achat execute. */
    public void credit(BigDecimal amount) {
        this.quantity = this.quantity.add(amount);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getLockedQuantity() { return lockedQuantity; }
    public long getVersion() { return version; }
}
