package com.exchange.matching.dto;

import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.Role;

import java.math.BigDecimal;
import java.time.Instant;

/** Ligne de supervision d'un utilisateur. cashBalance est null pour un administrateur. */
public record AdminUserResponse(
    String username,
    String displayName,
    String email,
    Role role,
    AccountStatus status,
    Instant createdAt,
    BigDecimal cashBalance,
    long activeOrders
) {}
