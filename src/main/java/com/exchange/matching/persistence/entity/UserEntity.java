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

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
