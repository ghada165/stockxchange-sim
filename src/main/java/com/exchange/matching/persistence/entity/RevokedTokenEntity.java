package com.exchange.matching.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Jeton JWT invalide par une deconnexion, identifie par sa claim "jti". */
@Entity
@Table(name = "revoked_tokens")
public class RevokedTokenEntity {

    @Id
    private String jti;

    @Column(nullable = false)
    private String username;

    /** Date d'expiration du jeton: apres elle, la ligne peut etre supprimee. */
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant revokedAt;

    protected RevokedTokenEntity() {
    }

    public RevokedTokenEntity(String jti, String username, Instant expiresAt) {
        this.jti = jti;
        this.username = username;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }

    public String getJti() { return jti; }
    public String getUsername() { return username; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
