package com.exchange.matching.dto;

import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.Role;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ligne de supervision d'un utilisateur. cashBalance est null pour un administrateur,
 * lockedUntil est non null tant que le compte est verrouille apres trop d'echecs de connexion.
 */
public record AdminUserResponse(
    String username,
    String displayName,
    String email,
    Role role,
    AccountStatus status,
    Instant createdAt,
    Instant lockedUntil,
    BigDecimal cashBalance,
    long activeOrders
) {}
