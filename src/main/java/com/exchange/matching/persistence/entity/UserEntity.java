package com.exchange.matching.persistence.entity;

import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.Role;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Compte utilisateur (Trader ou Admin). Le mot de passe n'est stocke que hache (BCrypt). */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    private String displayName;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    /** Echecs de connexion consecutifs (remis a zero a la premiere connexion reussie). */
    @Column(nullable = false)
    private int failedLoginAttempts;

    /** Compte verrouille jusqu'a cette date apres trop d'echecs (null = pas verrouille). */
    private Instant lockedUntil;

    /** Version des jetons: un JWT dont la claim "ver" differe est refuse. */
    @Column(nullable = false)
    private int tokenVersion;

    protected UserEntity() {
    }

    public UserEntity(String username, String passwordHash, String displayName, String email, Role role) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Enregistre un echec de connexion. Au-dela de {@code maxAttempts} echecs
     * consecutifs, le compte est verrouille pendant {@code lockDuration}.
     *
     * @return true si cet echec vient de declencher le verrouillage
     */
    public boolean registerFailedLogin(int maxAttempts, java.time.Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            failedLoginAttempts = 0;
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Invalide tous les jetons deja emis pour ce compte: ils portent l'ancienne
     * version. (Une comparaison de dates ne suffirait pas: la date d'emission
     * d'un JWT n'est precise qu'a la seconde.)
     */
    public void revokeAllTokens() {
        tokenVersion++;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        revokeAllTokens();
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public int getTokenVersion() { return tokenVersion; }
}
