package com.exchange.matching.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Portefeuille cash d'un utilisateur (une seule devise pour simplifier: USD).
 *
 * `lockedBalance` represente les fonds reserves par des ordres BUY LIMIT
 * actifs (prix x quantite restante). `balance` est le solde total; le solde
 * DISPONIBLE pour un nouvel ordre = balance - lockedBalance.
 *
 * @Version active le verrouillage optimiste JPA: si deux transactions
 * modifient le meme wallet en parallele, la seconde a valider echoue avec
 * une OptimisticLockException plutot que d'ecraser silencieusement la
 * premiere ecriture. C'est le filet de securite cote DB qui complete le
 * verrouillage par symbole du MatchingEngine (qui, lui, ne protege que le
 * carnet d'ordres, pas les mises a jour de solde).
 */
@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(columnNames = "userId"))
public class WalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lockedBalance = BigDecimal.ZERO;

    @Version
    private long version;

    protected WalletEntity() {
    }

    public WalletEntity(String userId, BigDecimal initialBalance) {
        this.userId = userId;
        this.balance = initialBalance;
    }

    public BigDecimal availableBalance() {
        return balance.subtract(lockedBalance);
    }

    public void lock(BigDecimal amount) {
        if (amount.compareTo(availableBalance()) > 0) {
            throw new IllegalStateException("Fonds insuffisants pour bloquer " + amount);
        }
        this.lockedBalance = this.lockedBalance.add(amount);
    }

    public void unlock(BigDecimal amount) {
        this.lockedBalance = this.lockedBalance.subtract(amount);
    }

    /** Debit du solde reel (le cash quitte le wallet). A utiliser avec unlock() pour regler un achat. */
    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getLockedBalance() { return lockedBalance; }
    public long getVersion() { return version; }
}
