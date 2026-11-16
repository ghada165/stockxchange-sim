package com.exchange.matching.persistence.entity;

import com.exchange.matching.model.AccountStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Action cotee sur la plateforme (referentiel du module Marche). */
@Entity
@Table(name = "symbols")
public class SymbolEntity {

    @Id
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal referencePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    protected SymbolEntity() {
    }

    public SymbolEntity(String symbol, String name, BigDecimal referencePrice) {
        this.symbol = symbol;
        this.name = name;
        this.referencePrice = referencePrice;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public boolean isTradable() {
        return status == AccountStatus.ACTIVE;
    }

    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public void setReferencePrice(BigDecimal referencePrice) { this.referencePrice = referencePrice; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
